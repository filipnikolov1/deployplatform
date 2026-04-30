package dev.filipnikolov.vector.analyzer.timeline;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analyzer/apps/{appName}")
public class TimelineController {

    private final TimelineEventService service;

    public TimelineController(TimelineEventService service) {
        this.service = service;
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
                .map(e -> Map.<String, Object>of(
                        "id",          e.getId(),
                        "appName",     e.getAppName(),
                        "eventType",   e.getEventType(),
                        "commitSha",   e.getCommitSha()     != null ? e.getCommitSha() : "",
                        "occurredAt",  e.getOccurredAt().toString(),
                        "metadata",    e.getMetadataJson()  != null ? e.getMetadataJson() : "{}"))
                .toList();

        return ResponseEntity.ok(events);
    }
}
