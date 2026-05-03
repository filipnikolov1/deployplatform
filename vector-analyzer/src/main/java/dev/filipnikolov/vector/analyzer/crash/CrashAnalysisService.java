package dev.filipnikolov.vector.analyzer.crash;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.analyzer.commit.GitHubCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CrashAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CrashAnalysisService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** How many log lines preceding the crash are bundled as evidence. */
    private static final int CRASH_LOG_WINDOW = 80;

    /** Lookback for finding stack frames in log lines. */
    private static final int STACK_TRACE_SCAN_LINES = 200;

    private final JdbcTemplate jdbc;
    private final GitHubCacheService github;
    private final AnalysisStreamBroadcaster broadcaster;

    public CrashAnalysisService(JdbcTemplate jdbc,
                                GitHubCacheService github,
                                AnalysisStreamBroadcaster broadcaster) {
        this.jdbc = jdbc;
        this.github = github;
        this.broadcaster = broadcaster;
    }

    /**
     * Generate (or fetch existing) crash_analysis row for a given CRASHED deployment_event id.
     * Idempotent: re-invocation returns the existing row.
     */
    @Transactional
    public CrashAnalysis generateForCrashEvent(long crashEventId) {
        Optional<CrashAnalysis> existing = findByCrashEventId(crashEventId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Map<String, Object> crash;
        try {
            crash = jdbc.queryForMap(
                    "SELECT id, app_name, created_at FROM public.deployment_event " +
                    "WHERE id = ? AND event_type = 'CRASHED'", crashEventId);
        } catch (EmptyResultDataAccessException e) {
            log.warn("CRASHED event id={} not found — skipping crash analysis", crashEventId);
            return null;
        }

        String appName = (String) crash.get("app_name");
        if (appName == null) {
            log.warn("CRASHED event id={} has null app_name — skipping", crashEventId);
            return null;
        }
        LocalDateTime crashTime = toLocalDateTime(crash.get("created_at"));

        String suspectSha = findSuspectCommitSha(appName, crashTime);
        String lastGoodSha = findLastGoodCommitSha(appName, crashTime, suspectSha);
        LocalDateTime lastDeployTime = findLastDeployTime(appName, crashTime);

        Long timeSinceDeployMin = (lastDeployTime != null)
                ? ChronoUnit.MINUTES.between(lastDeployTime, crashTime)
                : null;

        long crashCountForCommit = countCrashesForCommit(appName, suspectSha) + 1;

        List<Map<String, Object>> logLines = fetchCrashLogWindow(appName, crashTime);

        StackTraceParser.Frame topFrame = StackTraceParser.findTopFrame(
                logLines.stream()
                        .limit(STACK_TRACE_SCAN_LINES)
                        .map(r -> (String) r.get("line"))
                        .toList()
        ).orElse(null);

        String suspectFile = topFrame != null ? topFrame.file() : null;
        Integer suspectLine = topFrame != null ? topFrame.line() : null;

        // Diff hunk between suspect and last_good
        String repoSlug = resolveRepoSlug(appName);
        String diffJson = (repoSlug != null && lastGoodSha != null && suspectSha != null)
                ? github.fetchDiff(repoSlug, lastGoodSha, suspectSha)
                : null;

        // Fallback suspect file from single-file change in suspect commit
        if (suspectFile == null && diffJson != null) {
            List<String> changedFiles = extractChangedFiles(diffJson);
            if (changedFiles.size() == 1) {
                suspectFile = changedFiles.get(0);
            }
        }

        Map<String, Object> commitMeta = (repoSlug != null && suspectSha != null)
                ? loadCommitMetadata(repoSlug, suspectSha)
                : Map.of();

        List<Map<String, Object>> evidence = buildEvidence(logLines, diffJson, commitMeta, repoSlug, suspectSha);
        Map<String, Object> signals = Map.of(
                "timeSinceDeployMinutes", timeSinceDeployMin,
                "crashCountForCommit", crashCountForCommit,
                "crashedAt", crashTime.toString(),
                "lastDeployedAt", lastDeployTime != null ? lastDeployTime.toString() : null
        );

        String evidenceText = writeJson(evidence);
        String signalsText = writeJson(signals);

        jdbc.update("""
                INSERT INTO analyzer.crash_analysis
                    (app_name, crash_event_id, suspect_commit_sha, last_good_commit_sha,
                     suspect_file_path, suspect_line, ai_narration, ai_provider_used,
                     evidence_json, signals_json, generated_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?, NOW())
                ON CONFLICT (crash_event_id) DO NOTHING
                """,
                appName, crashEventId, suspectSha, lastGoodSha,
                suspectFile, suspectLine, evidenceText, signalsText);

        CrashAnalysis saved = findByCrashEventId(crashEventId).orElse(null);
        if (saved != null) {
            broadcaster.publish(appName, "crashAnalysisCreated", Map.of(
                    "crashId", crashEventId,
                    "analysisId", saved.getId()
            ));
        }
        return saved;
    }

    public Optional<CrashAnalysis> findByCrashEventId(long crashEventId) {
        List<CrashAnalysis> rows = jdbc.query(
                "SELECT * FROM analyzer.crash_analysis WHERE crash_event_id = ?",
                (rs, i) -> mapRow(rs), crashEventId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<CrashAnalysis> findByAppAndCrashId(String appName, long crashEventId) {
        List<CrashAnalysis> rows = jdbc.query(
                "SELECT * FROM analyzer.crash_analysis WHERE app_name = ? AND crash_event_id = ?",
                (rs, i) -> mapRow(rs), appName, crashEventId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /* ---------------------- helpers ---------------------- */

    private String findSuspectCommitSha(String appName, LocalDateTime crashTime) {
        // Most recent DEPLOY_FINISHED success that occurred at or before the crash
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT commit_sha FROM public.deployment_event
                WHERE app_name = ? AND event_type = 'DEPLOY_FINISHED' AND status = 'SUCCESS'
                  AND commit_sha IS NOT NULL AND created_at <= ?
                ORDER BY created_at DESC LIMIT 1
                """, appName, crashTime);
        if (!rows.isEmpty()) {
            return (String) rows.get(0).get("commit_sha");
        }
        // Fallback: read deployment.commit_sha (current running commit) if no event found
        List<Map<String, Object>> dep = jdbc.queryForList(
                "SELECT commit_sha FROM public.deployment WHERE app_name = ? LIMIT 1", appName);
        return dep.isEmpty() ? null : (String) dep.get(0).get("commit_sha");
    }

    private String findLastGoodCommitSha(String appName, LocalDateTime crashTime, String suspectSha) {
        if (suspectSha == null) return null;
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT commit_sha FROM public.deployment_event
                WHERE app_name = ? AND event_type = 'DEPLOY_FINISHED' AND status = 'SUCCESS'
                  AND commit_sha IS NOT NULL AND commit_sha != ? AND created_at <= ?
                ORDER BY created_at DESC LIMIT 1
                """, appName, suspectSha, crashTime);
        return rows.isEmpty() ? null : (String) rows.get(0).get("commit_sha");
    }

    private LocalDateTime findLastDeployTime(String appName, LocalDateTime crashTime) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT created_at FROM public.deployment_event
                WHERE app_name = ? AND event_type = 'DEPLOY_FINISHED' AND status = 'SUCCESS'
                  AND created_at <= ?
                ORDER BY created_at DESC LIMIT 1
                """, appName, crashTime);
        return rows.isEmpty() ? null : toLocalDateTime(rows.get(0).get("created_at"));
    }

    private long countCrashesForCommit(String appName, String suspectSha) {
        if (suspectSha == null) return 0;
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM analyzer.crash_analysis WHERE app_name = ? AND suspect_commit_sha = ?",
                Long.class, appName, suspectSha);
        return n != null ? n : 0;
    }

    private List<Map<String, Object>> fetchCrashLogWindow(String appName, LocalDateTime crashTime) {
        return jdbc.queryForList(
                """
                SELECT id, timestamp, stream, line FROM analyzer.log_entry
                WHERE app_name = ? AND timestamp <= ?
                ORDER BY timestamp DESC LIMIT ?
                """, appName, crashTime, CRASH_LOG_WINDOW);
    }

    private Map<String, Object> loadCommitMetadata(String repoSlug, String sha) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT author, authored_at, message FROM analyzer.commit_cache WHERE repo_full_name = ? AND sha = ?",
                repoSlug, sha);
        if (rows.isEmpty()) return Map.of();
        Map<String, Object> r = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sha", sha);
        result.put("author", r.get("author"));
        result.put("authoredAt", r.get("authored_at") != null ? r.get("authored_at").toString() : null);
        result.put("message", r.get("message"));
        return result;
    }

    private List<Map<String, Object>> buildEvidence(List<Map<String, Object>> logLines,
                                                     String diffJson,
                                                     Map<String, Object> commitMeta,
                                                     String repoSlug,
                                                     String suspectSha) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        int id = 1;
        // Logs in chronological order (oldest first) so the user can read top-down
        List<Map<String, Object>> chronological = new ArrayList<>(logLines);
        java.util.Collections.reverse(chronological);
        for (Map<String, Object> row : chronological) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id++);
            item.put("type", "log");
            item.put("source", row.get("stream"));
            item.put("timestamp", row.get("timestamp") != null ? row.get("timestamp").toString() : null);
            item.put("content", row.get("line"));
            evidence.add(item);
        }
        if (diffJson != null) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id++);
            item.put("type", "diff");
            item.put("source", "github");
            item.put("repoSlug", repoSlug);
            item.put("suspectSha", suspectSha);
            item.put("content", diffJson);
            evidence.add(item);
        }
        if (commitMeta != null && !commitMeta.isEmpty()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id++);
            item.put("type", "commit");
            item.put("source", "github");
            item.put("content", commitMeta);
            evidence.add(item);
        }
        return evidence;
    }

    private List<String> extractChangedFiles(String diffJson) {
        try {
            Map<String, Object> parsed = MAPPER.readValue(diffJson, new TypeReference<>() {});
            Object filesObj = parsed.get("files");
            if (!(filesObj instanceof List<?> files)) return List.of();
            List<String> names = new ArrayList<>();
            for (Object f : files) {
                if (f instanceof Map<?, ?> fm && fm.get("filename") instanceof String name) {
                    names.add(name);
                }
            }
            return names;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String resolveRepoSlug(String appName) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT repo_url FROM public.deployment WHERE app_name = ? AND deleted_at IS NULL AND repo_url IS NOT NULL LIMIT 1",
                appName);
        if (rows.isEmpty()) return null;
        String url = (String) rows.get(0).get("repo_url");
        if (url == null) return null;
        return url.replaceFirst("^https?://github\\.com/", "").replaceFirst("\\.git$", "");
    }

    private String writeJson(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { return "{}"; }
    }

    private CrashAnalysis mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        CrashAnalysis a = new CrashAnalysis();
        a.setId(rs.getLong("id"));
        a.setAppName(rs.getString("app_name"));
        a.setCrashEventId(rs.getLong("crash_event_id"));
        a.setSuspectCommitSha(rs.getString("suspect_commit_sha"));
        a.setLastGoodCommitSha(rs.getString("last_good_commit_sha"));
        a.setSuspectFilePath(rs.getString("suspect_file_path"));
        int line = rs.getInt("suspect_line");
        a.setSuspectLine(rs.wasNull() ? null : line);
        a.setAiNarration(rs.getString("ai_narration"));
        a.setAiProviderUsed(rs.getString("ai_provider_used"));
        a.setAiNarrationStatus(rs.getString("ai_narration_status"));
        a.setAiRegenerateCount(rs.getInt("ai_regenerate_count"));
        a.setAiFailureReason(rs.getString("ai_failure_reason"));
        a.setEvidenceJson(rs.getString("evidence_json"));
        a.setSignalsJson(rs.getString("signals_json"));
        java.sql.Timestamp ts = rs.getTimestamp("generated_at");
        a.setGeneratedAt(ts != null ? ts.toLocalDateTime() : null);
        return a;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return LocalDateTime.parse(value.toString());
    }
}
