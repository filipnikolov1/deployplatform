package dev.filipnikolov.vector.analyzer.timeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.filipnikolov.vector.analyzer.crash.CrashAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TimelineEventService {

    private static final Logger log = LoggerFactory.getLogger(TimelineEventService.class);

    private static final Set<String> RELEVANT_TYPES = Set.of(
            "DEPLOY_FINISHED", "FAILED", "MANUAL_ROLLBACK", "CRASHED", "RESTARTED");

    private static final String BACKFILL_SQL = """
            INSERT INTO analyzer.timeline_event
                (app_name, event_type, commit_sha, occurred_at, metadata_json, source_event_id)
            SELECT
                de.app_name,
                CASE de.event_type
                    WHEN 'DEPLOY_FINISHED'  THEN 'DEPLOY'
                    WHEN 'FAILED'           THEN 'DEPLOY'
                    WHEN 'MANUAL_ROLLBACK'  THEN 'DEPLOY'
                    WHEN 'CRASHED'          THEN 'CRASH'
                    WHEN 'RESTARTED'        THEN 'RESTART'
                END,
                de.commit_sha,
                de.created_at,
                json_build_object('eventType', de.event_type, 'status', de.status)::text,
                de.id
            FROM public.deployment_event de
            WHERE de.app_name IS NOT NULL
              AND de.event_type IN ('DEPLOY_FINISHED','FAILED','MANUAL_ROLLBACK','CRASHED','RESTARTED')
            ON CONFLICT (source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING
            """;

    private final TimelineEventRepository repo;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    @Lazy
    private CrashAnalysisService crashAnalysisService;

    public TimelineEventService(TimelineEventRepository repo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    public void performStartupSync() {
        long count = repo.count();
        if (count == 0) {
            log.info("Timeline empty — running full backfill from public.deployment_event");
            int inserted = jdbc.update(BACKFILL_SQL);
            log.info("Backfill complete: {} events inserted", inserted);
        } else {
            long maxId = repo.findMaxSourceEventId().orElse(0L);
            log.info("Timeline non-empty — catching up from source_event_id > {}", maxId);
            catchUpFrom(maxId);
        }
    }

    public void catchUpSync() {
        long maxId = repo.findMaxSourceEventId().orElse(0L);
        catchUpFrom(maxId);
    }

    private void catchUpFrom(long afterId) {
        // CATCHUP_SQL inserts the extra `de.id > ?` filter
        String sql = """
                INSERT INTO analyzer.timeline_event
                    (app_name, event_type, commit_sha, occurred_at, metadata_json, source_event_id)
                SELECT
                    de.app_name,
                    CASE de.event_type
                        WHEN 'DEPLOY_FINISHED'  THEN 'DEPLOY'
                        WHEN 'FAILED'           THEN 'DEPLOY'
                        WHEN 'MANUAL_ROLLBACK'  THEN 'DEPLOY'
                        WHEN 'CRASHED'          THEN 'CRASH'
                        WHEN 'RESTARTED'        THEN 'RESTART'
                    END,
                    de.commit_sha,
                    de.created_at,
                    json_build_object('eventType', de.event_type, 'status', de.status)::text,
                    de.id
                FROM public.deployment_event de
                WHERE de.id > ?
                  AND de.app_name IS NOT NULL
                  AND de.event_type IN ('DEPLOY_FINISHED','FAILED','MANUAL_ROLLBACK','CRASHED','RESTARTED')
                ON CONFLICT (source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING
                """;
        int n = jdbc.update(sql, afterId);
        if (n > 0) {
            log.info("Catch-up inserted {} timeline events (after id={})", n, afterId);
        }
    }

    @Transactional
    public ProcessedDeploymentEvent processNotification(String payload) {
        try {
            long sourceId = sourceIdFromNotification(payload);

            // Fetch full event row for commit_sha and exact timestamp
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT app_name, event_type, commit_sha, created_at, status FROM public.deployment_event WHERE id = ?",
                    sourceId);

            String eventType = row.get("event_type").toString();
            if (!RELEVANT_TYPES.contains(eventType)) {
                return null;
            }
            String appName = (String) row.get("app_name");
            String commitSha = (String) row.get("commit_sha");
            Object createdAt = row.get("created_at");
            LocalDateTime occurredAt = toLocalDateTime(createdAt);

            String timelineType = mapEventType(eventType);
            String metadata = mapper.writeValueAsString(Map.of("eventType", eventType, "status", row.get("status")));

            jdbc.update("""
                    INSERT INTO analyzer.timeline_event
                        (app_name, event_type, commit_sha, occurred_at, metadata_json, source_event_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING
                    """,
                    appName, timelineType, commitSha, occurredAt, metadata, sourceId);

            if ("CRASHED".equals(eventType)) {
                try {
                    crashAnalysisService.generateForCrashEvent(sourceId);
                } catch (Exception e) {
                    log.error("Crash analysis generation failed for event {}: {}", sourceId, e.getMessage());
                }
            }
            return new ProcessedDeploymentEvent(sourceId, appName, eventType, row.get("status").toString());

        } catch (Exception e) {
            log.error("Failed to process notification payload: {} — {}", payload, e.getMessage());
            return null;
        }
    }

    @Transactional
    public void insertCommitEvent(String appName, String sha, String message,
                                  String author, LocalDateTime timestamp) {
        if (repo.existsByAppNameAndEventTypeAndCommitSha(appName, "COMMIT", sha)) {
            return;
        }
        try {
            String metadata = mapper.writeValueAsString(Map.of("message", message, "author", author));
            TimelineEvent e = new TimelineEvent();
            e.setAppName(appName);
            e.setEventType("COMMIT");
            e.setCommitSha(sha);
            e.setOccurredAt(timestamp);
            e.setMetadataJson(metadata);
            repo.save(e);
        } catch (Exception ex) {
            log.error("Failed to insert COMMIT event for {} sha={}: {}", appName, sha, ex.getMessage());
        }
    }

    public List<TimelineEvent> getTimeline(String appName, LocalDateTime from, LocalDateTime to) {
        return repo.findByAppNameAndOccurredAtBetweenOrderByOccurredAtDesc(appName, from, to);
    }

    private static String mapEventType(String deploymentEventType) {
        return switch (deploymentEventType) {
            case "DEPLOY_FINISHED", "FAILED", "MANUAL_ROLLBACK" -> "DEPLOY";
            case "CRASHED" -> "CRASH";
            case "RESTARTED" -> "RESTART";
            default -> "DEPLOY";
        };
    }

    private long sourceIdFromNotification(String payload) throws Exception {
        String trimmed = payload.trim();
        if (trimmed.matches("\\d+")) {
            return Long.parseLong(trimmed);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.readValue(trimmed, Map.class);
        return ((Number) map.get("id")).longValue();
    }

    public record ProcessedDeploymentEvent(long id, String appName, String eventType, String status) {}

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        String s = value.toString();
        // Offset-bearing strings (e.g. "2026-05-20T12:00:00+02:00") must be normalized to
        // UTC LocalDateTime, otherwise the timeline either drops the event or mis-orders it.
        try {
            return OffsetDateTime.parse(s).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {}
        for (DateTimeFormatter fmt : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))) {
            try { return LocalDateTime.parse(s, fmt); } catch (DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("Cannot parse timestamp: " + s);
    }
}
