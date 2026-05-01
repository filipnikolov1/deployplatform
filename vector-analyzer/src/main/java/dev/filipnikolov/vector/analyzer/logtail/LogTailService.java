package dev.filipnikolov.vector.analyzer.logtail;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LogTailService {

    private static final Logger log = LoggerFactory.getLogger(LogTailService.class);

    private final JdbcTemplate jdbc;
    private final String apiUrl;
    private final String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "log-tail");
        t.setDaemon(true);
        return t;
    });

    /** appName → Future of the currently running tail loop */
    private final ConcurrentHashMap<String, Future<?>> activeTails = new ConcurrentHashMap<>();
    /** appName → cancel flag for the running tail */
    private final ConcurrentHashMap<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public LogTailService(JdbcTemplate jdbc,
                          @Value("${vector.api.internal-url:http://vector-api:8082}") String apiUrl,
                          @Value("${app.api-key:}") String apiKey) {
        this.jdbc = jdbc;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    @PostConstruct
    public void startAll() {
        if (apiUrl == null || apiUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn("LogTailService: VECTOR_API_INTERNAL_URL or API key not configured — log tailing disabled");
            return;
        }

        List<Map<String, Object>> apps = jdbc.queryForList(
                "SELECT app_name FROM public.deployment WHERE deleted_at IS NULL");
        for (Map<String, Object> row : apps) {
            String appName = (String) row.get("app_name");
            if (appName != null) startTail(appName);
        }
        log.info("LogTailService: started tails for {} apps", apps.size());
    }

    /** Called by PostgresEventListener when a DEPLOY_FINISHED event arrives. */
    public void onDeployFinished(String appName) {
        if (apiUrl == null || apiUrl.isBlank() || apiKey == null || apiKey.isBlank()) return;
        if (shutdown.get()) return;
        // Cancel existing tail and start fresh — deployment ID may have changed
        cancelTail(appName);
        startTail(appName);
    }

    @PreDestroy
    public void shutdown() {
        shutdown.set(true);
        cancelFlags.forEach((app, flag) -> flag.set(true));
        executor.shutdownNow();
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void startTail(String appName) {
        AtomicBoolean cancel = new AtomicBoolean(false);
        cancelFlags.put(appName, cancel);
        Future<?> future = executor.submit(() -> tailWithBackoff(appName, cancel));
        activeTails.put(appName, future);
    }

    private void cancelTail(String appName) {
        AtomicBoolean flag = cancelFlags.get(appName);
        if (flag != null) flag.set(true);
        Future<?> f = activeTails.remove(appName);
        if (f != null) f.cancel(true);
    }

    private void tailWithBackoff(String appName, AtomicBoolean cancel) {
        long backoffMs = 1_000;
        while (!shutdown.get() && !cancel.get()) {
            try {
                tail(appName, cancel);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (!cancel.get() && !shutdown.get()) {
                    log.debug("Log tail for {} failed (retrying in {}s): {}", appName, backoffMs / 1000, e.getMessage());
                }
            }
            if (shutdown.get() || cancel.get()) return;
            try {
                Thread.sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void tail(String appName, AtomicBoolean cancel) throws Exception {
        // Look up current deployment ID and commit SHA
        List<Map<String, Object>> deps = jdbc.queryForList(
                "SELECT id, commit_sha FROM public.deployment WHERE app_name = ? AND deleted_at IS NULL LIMIT 1",
                appName);
        if (deps.isEmpty()) return;

        long deploymentId = ((Number) deps.get(0).get("id")).longValue();
        String commitSha = (String) deps.get(0).get("commit_sha");

        String url = apiUrl + "/api/internal/apps/" + appName + "/logs/stream";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", apiKey)
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            log.debug("Log stream for {} returned HTTP {}", appName, response.statusCode());
            return;
        }

        List<String[]> buffer = new ArrayList<>();
        long lastFlushMs = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
            String line;
            while (!shutdown.get() && !cancel.get() && (line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if (!data.isBlank()) {
                        buffer.add(new String[]{data, "stdout", LocalDateTime.now().toString()});
                    }
                }
                // Flush every 100 lines or every 2 seconds
                long now = System.currentTimeMillis();
                if (buffer.size() >= 100 || (buffer.size() > 0 && now - lastFlushMs >= 2_000)) {
                    flushBuffer(appName, deploymentId, commitSha, buffer);
                    buffer.clear();
                    lastFlushMs = now;
                }
            }
        }
        // Final flush
        if (!buffer.isEmpty()) {
            flushBuffer(appName, deploymentId, commitSha, buffer);
        }
    }

    private void flushBuffer(String appName, long deploymentId, String commitSha, List<String[]> lines) {
        if (lines.isEmpty()) return;
        try {
            jdbc.batchUpdate(
                    "INSERT INTO analyzer.log_entry (app_name, deployment_id, commit_sha, timestamp, stream, line) VALUES (?, ?, ?, ?::timestamp, ?, ?)",
                    lines,
                    lines.size(),
                    (ps, entry) -> {
                        ps.setString(1, appName);
                        ps.setLong(2, deploymentId);
                        ps.setString(3, commitSha);
                        ps.setString(4, entry[2]);
                        ps.setString(5, entry[1]);
                        ps.setString(6, entry[0]);
                    });
        } catch (Exception e) {
            log.error("Failed to flush log buffer for {}: {}", appName, e.getMessage());
        }
    }
}
