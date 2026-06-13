package dev.filipnikolov.vector.analyzer.timeline;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analyzer/apps/{appName}")
public class TimelineController {

    private final TimelineEventService service;
    private final TimelineEventRepository repo;
    private final JdbcTemplate jdbc;

    public TimelineController(TimelineEventService service, TimelineEventRepository repo, JdbcTemplate jdbc) {
        this.service = service;
        this.repo = repo;
        this.jdbc = jdbc;
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<Map<String, Object>>> timeline(
            @PathVariable String appName,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        LocalDateTime effectiveFrom = from != null ? from : LocalDateTime.now().minusWeeks(2);
        LocalDateTime effectiveTo   = to   != null ? to   : LocalDateTime.now();

        List<Map<String, Object>> events = service.getTimeline(appName, effectiveFrom, effectiveTo)
                .stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",            e.getId());
                    m.put("appName",       e.getAppName());
                    m.put("eventType",     e.getEventType());
                    m.put("commitSha",     e.getCommitSha()    != null ? e.getCommitSha() : "");
                    m.put("occurredAt",    e.getOccurredAt().toString());
                    m.put("metadata",      e.getMetadataJson() != null ? e.getMetadataJson() : "{}");
                    m.put("sourceEventId", e.getSourceEventId());
                    return m;
                })
                .toList();

        return ResponseEntity.ok(events);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(@PathVariable String appName) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : repo.countEventsByType(appName, since)) {
            counts.put((String) row[0], (Long) row[1]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appName",       appName);
        result.put("deploys30d",    counts.getOrDefault("DEPLOY",  0L));
        result.put("crashes30d",    counts.getOrDefault("CRASH",   0L));
        result.put("restarts30d",   counts.getOrDefault("RESTART", 0L));
        result.put("commits30d",    counts.getOrDefault("COMMIT",  0L));
        result.put("lastDeployedAt", repo.findLastDeployTime(appName)
                .map(LocalDateTime::toString).orElse(null));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/deploys")
    public ResponseEntity<List<Map<String, Object>>> deploys(@PathVariable String appName) {
        List<Map<String, Object>> events = repo
                .findTop10ByAppNameAndEventTypeOrderByOccurredAtDesc(appName, "DEPLOY")
                .stream()
                .map(e -> Map.<String, Object>of(
                        "id",         e.getId(),
                        "commitSha",  e.getCommitSha()    != null ? e.getCommitSha() : "",
                        "occurredAt", e.getOccurredAt().toString(),
                        "metadata",   e.getMetadataJson() != null ? e.getMetadataJson() : "{}"))
                .toList();

        return ResponseEntity.ok(events);
    }

    /**
     * GET /api/analyzer/apps/{appName}/uptime?days=30
     * Returns { percent, uptimeMs, downtimeMs } computed from deployment_event transitions
     * over the last N days. Considers the app RUNNING after a DEPLOY_FINISHED SUCCESS,
     * and DOWN after CRASHED / RESTARTED / STOPPED events.
     */
    @GetMapping("/uptime")
    public ResponseEntity<Map<String, Object>> uptime(
            @PathVariable String appName,
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LocalDateTime now = LocalDateTime.now();

        // Fetch all relevant events ordered by time ascending
        String sql = """
                SELECT event_type, status, created_at
                FROM public.deployment_event
                WHERE app_name = ?
                  AND created_at >= ?
                  AND event_type IN ('DEPLOY_FINISHED','CRASHED','RESTARTED','STOPPED')
                ORDER BY created_at ASC
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql, appName, since);

        long windowMs = java.time.Duration.between(since, now).toMillis();

        // State at window start: last relevant event BEFORE the window decides up/down.
        List<Map<String, Object>> beforeWindow = jdbc.queryForList("""
                SELECT event_type, status FROM public.deployment_event
                WHERE app_name = ?
                  AND created_at < ?
                  AND event_type IN ('DEPLOY_FINISHED','CRASHED','RESTARTED','STOPPED')
                ORDER BY created_at DESC
                LIMIT 1
                """, appName, since);
        boolean runningAtStart = false;
        if (!beforeWindow.isEmpty()) {
            String t = beforeWindow.get(0).get("event_type").toString();
            String s = beforeWindow.get(0).get("status") != null ? beforeWindow.get(0).get("status").toString() : "";
            runningAtStart = ("DEPLOY_FINISHED".equals(t) || "RESTARTED".equals(t)) && "SUCCESS".equals(s);
        }

        long uptimeMs = computeUptimeMs(runningAtStart, rows, since, now);
        uptimeMs = Math.min(uptimeMs, windowMs);
        long downtimeMs = Math.max(0, windowMs - uptimeMs);
        double percent = windowMs > 0 ? Math.round((uptimeMs * 1000.0 / windowMs)) / 10.0 : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("percent", percent);
        result.put("uptimeMs", uptimeMs);
        result.put("downtimeMs", downtimeMs);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/analyzer/apps/{appName}/avg-pull?days=30
     * Returns { avgMs, count } computed as AVG(duration_ms) over DEPLOY_FINISHED SUCCESS events.
     */
    @GetMapping("/avg-pull")
    public ResponseEntity<Map<String, Object>> avgPull(
            @PathVariable String appName,
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);

        String sql = """
                SELECT AVG(duration_ms) AS avg_ms, COUNT(*) AS cnt
                FROM public.deployment_event
                WHERE app_name = ?
                  AND event_type = 'DEPLOY_FINISHED'
                  AND status = 'SUCCESS'
                  AND duration_ms IS NOT NULL
                  AND created_at >= ?
                """;

        Map<String, Object> row = jdbc.queryForMap(sql, appName, since);
        Object avgRaw = row.get("avg_ms");
        Object cntRaw = row.get("cnt");

        long avgMs = avgRaw != null ? ((Number) avgRaw).longValue() : 0L;
        long count = cntRaw != null ? ((Number) cntRaw).longValue() : 0L;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("avgMs", avgMs);
        result.put("count", count);
        return ResponseEntity.ok(result);
    }

    /** Walks deployment events and accumulates uptime. RESTARTED SUCCESS = recovery (up);
     *  RESTARTED FAILURE = failed restart (down); CRASHED/STOPPED = down. */
    static long computeUptimeMs(boolean runningAtStart,
                                List<Map<String, Object>> rows,
                                LocalDateTime since, LocalDateTime now) {
        long uptimeMs = 0L;
        Long runningStart = runningAtStart ? since.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() : null;

        for (Map<String, Object> row : rows) {
            String type = row.get("event_type").toString();
            String status = row.get("status") != null ? row.get("status").toString() : "";
            long epochMs = toLocalDt(row.get("created_at")).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();

            boolean success = "SUCCESS".equals(status);
            boolean becomesRunning = ("DEPLOY_FINISHED".equals(type) && success)
                    || ("RESTARTED".equals(type) && success);
            boolean becomesDown = "CRASHED".equals(type) || "STOPPED".equals(type)
                    || ("RESTARTED".equals(type) && !success);

            if (becomesRunning && runningStart == null) {
                runningStart = epochMs;
            } else if (becomesDown && runningStart != null) {
                uptimeMs += (epochMs - runningStart);
                runningStart = null;
            }
        }

        if (runningStart != null) {
            uptimeMs += now.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() - runningStart;
        }
        return uptimeMs;
    }

    private static java.time.LocalDateTime toLocalDt(Object value) {
        if (value instanceof java.time.LocalDateTime ldt) return ldt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return java.time.LocalDateTime.parse(value.toString(),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
    }
}
