package dev.filipnikolov.vector.analyzer.github;

import dev.filipnikolov.vector.analyzer.timeline.TimelineEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class GitHubCommitSyncJob {

    private static final Logger log = LoggerFactory.getLogger(GitHubCommitSyncJob.class);
    private static final long RATE_LIMIT_INTERVAL_MS = 600; // 100 calls / min

    private final TimelineEventService timelineEventService;
    private final JdbcTemplate jdbc;
    private final String token;
    private final RestClient client;

    private long lastCallMs = 0;

    public GitHubCommitSyncJob(TimelineEventService timelineEventService,
                               JdbcTemplate jdbc,
                               @Value("${github.token:}") String token) {
        this.timelineEventService = timelineEventService;
        this.jdbc = jdbc;
        this.token = token;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));

        this.client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 5_000)
    public void sync() {
        if (token == null || token.isBlank()) {
            return;
        }

        List<Map<String, Object>> apps = jdbc.queryForList(
                "SELECT app_name, repo_url, branch FROM public.deployment WHERE deleted_at IS NULL AND repo_url IS NOT NULL");

        for (Map<String, Object> app : apps) {
            String appName = (String) app.get("app_name");
            String repoUrl = (String) app.get("repo_url");
            String branch  = (String) app.get("branch");

            String slug = parseSlug(repoUrl);
            if (slug.isEmpty()) continue;

            syncAppCommits(appName, slug, branch);
        }
    }

    @SuppressWarnings("unchecked")
    private void syncAppCommits(String appName, String slug, String branch) {
        try {
            rateLimitWait();

            String since = LocalDateTime.now().minusDays(30)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";

            var uriBuilder = "/repos/{slug}/commits?per_page=30&since={since}";
            var request = client.get()
                    .uri("/repos/" + slug + "/commits?per_page=30&since=" + since)
                    .header("Authorization", "Bearer " + token);

            if (branch != null && !branch.isBlank()) {
                request = client.get()
                        .uri("/repos/" + slug + "/commits?per_page=30&since=" + since + "&sha=" + branch)
                        .header("Authorization", "Bearer " + token);
            }

            List<Map<String, Object>> commits = request.retrieve().body(List.class);
            if (commits == null) return;

            for (Map<String, Object> c : commits) {
                String sha = (String) c.get("sha");
                Map<String, Object> inner = (Map<String, Object>) c.get("commit");
                if (sha == null || inner == null) continue;

                Map<String, Object> author = (Map<String, Object>) inner.get("author");
                String message    = (String) inner.get("message");
                String authorName = author != null ? (String) author.get("name")  : null;
                String dateStr    = author != null ? (String) author.get("date")  : null;

                LocalDateTime timestamp = dateStr != null
                        ? ZonedDateTime.parse(dateStr).toLocalDateTime()
                        : LocalDateTime.now();

                timelineEventService.insertCommitEvent(
                        appName, sha,
                        message  != null ? message  : "",
                        authorName != null ? authorName : "",
                        timestamp);
            }

        } catch (Exception e) {
            log.debug("GitHub commit sync skipped for {}: {}", appName, e.getMessage());
        }
    }

    private synchronized void rateLimitWait() throws InterruptedException {
        long now = System.currentTimeMillis();
        long wait = RATE_LIMIT_INTERVAL_MS - (now - lastCallMs);
        if (wait > 0) Thread.sleep(wait);
        lastCallMs = System.currentTimeMillis();
    }

    private static String parseSlug(String repoUrl) {
        if (repoUrl == null) return "";
        return repoUrl
                .replaceFirst("^https?://github\\.com/", "")
                .replaceFirst("\\.git$", "");
    }
}
