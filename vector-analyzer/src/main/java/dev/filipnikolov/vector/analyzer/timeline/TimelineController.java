package dev.filipnikolov.vector.analyzer.timeline;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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

    public TimelineController(TimelineEventService service, TimelineEventRepository repo) {
        this.service = service;
        this.repo = repo;
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
}
