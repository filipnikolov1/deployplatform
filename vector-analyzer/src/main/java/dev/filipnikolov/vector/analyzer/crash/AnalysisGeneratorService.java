package dev.filipnikolov.vector.analyzer.crash;

import dev.filipnikolov.vector.ai.AiProvider;
import dev.filipnikolov.vector.ai.AiProviderException;
import dev.filipnikolov.vector.ai.AiRequest;
import dev.filipnikolov.vector.ai.AiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class AnalysisGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisGeneratorService.class);

    /** Status values that mirror the ai_narration_status column. */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    private final JdbcTemplate jdbc;
    private final AiProvider aiProvider;
    private final AnalysisStreamBroadcaster broadcaster;
    private final int maxRegenerations;

    public AnalysisGeneratorService(JdbcTemplate jdbc,
                                    AiProvider aiProvider,
                                    AnalysisStreamBroadcaster broadcaster,
                                    @Value("${analyzer.ai.max-regenerations:1}") int maxRegenerations) {
        this.jdbc = jdbc;
        this.aiProvider = aiProvider;
        this.broadcaster = broadcaster;
        this.maxRegenerations = maxRegenerations;
    }

    /**
     * Kick off (or skip) an initial AI narration for a freshly-saved crash_analysis row.
     * Async so the calling LISTEN/NOTIFY handler returns immediately — generation can
     * take several seconds and must not back up the notification queue.
     */
    @Async
    public void generateInitialAsync(long crashAnalysisId) {
        runGeneration(crashAnalysisId, /* isRegeneration */ false);
    }

    /**
     * User-initiated regeneration. Synchronous so the controller can return the
     * fresh narration in its response — the user is staring at a spinner.
     * Enforces the max-regenerations cap inside a transaction-safe increment.
     */
    public CrashAnalysis regenerate(long crashAnalysisId) {
        Optional<CrashAnalysis> existing = loadById(crashAnalysisId);
        if (existing.isEmpty()) return null;
        CrashAnalysis a = existing.get();
        if (a.getAiRegenerateCount() >= maxRegenerations) {
            log.info("Regeneration denied for crash_analysis id={} (count={} >= max={})",
                    crashAnalysisId, a.getAiRegenerateCount(), maxRegenerations);
            return a;
        }
        runGeneration(crashAnalysisId, /* isRegeneration */ true);
        return loadById(crashAnalysisId).orElse(a);
    }

    public int maxRegenerations() {
        return maxRegenerations;
    }

    /* ------------------ internals ------------------ */

    private void runGeneration(long crashAnalysisId, boolean isRegeneration) {
        Optional<CrashAnalysis> opt = loadById(crashAnalysisId);
        if (opt.isEmpty()) {
            log.warn("crash_analysis id={} disappeared before AI generation", crashAnalysisId);
            return;
        }
        CrashAnalysis a = opt.get();
        markPending(crashAnalysisId, a);

        if (!aiProvider.isAvailable()) {
            log.info("AI provider unavailable — marking crash_analysis id={} UNAVAILABLE", crashAnalysisId);
            markUnavailable(crashAnalysisId, "AI provider not configured", isRegeneration, a);
            return;
        }

        String prompt = CrashPromptBuilder.build(a);
        log.info("Generating crash narration id={} (provider={}, prompt {} chars)",
                crashAnalysisId, aiProvider.providerName(), prompt.length());

        try {
            AiResponse response = aiProvider.analyze(new AiRequest(prompt));
            String text = response.text() != null ? response.text().trim() : "";
            if (text.isBlank()) {
                throw new AiProviderException("AI returned empty narration");
            }
            jdbc.update("""
                            UPDATE analyzer.crash_analysis
                               SET ai_narration = ?,
                                   ai_provider_used = ?,
                                   ai_narration_status = ?,
                                   ai_failure_reason = NULL,
                                   ai_regenerate_count = ai_regenerate_count + ?
                             WHERE id = ?
                            """,
                    text, response.providerUsed(), STATUS_AVAILABLE,
                    isRegeneration ? 1 : 0, crashAnalysisId);
            broadcaster.publish(a.getAppName(), "narrationCompleted", Map.of(
                    "crashId", a.getCrashEventId(),
                    "analysisId", crashAnalysisId,
                    "status", STATUS_AVAILABLE
            ));
            log.info("Narration generated for id={} ({} chars, provider={})",
                    crashAnalysisId, text.length(), response.providerUsed());
        } catch (AiProviderException e) {
            log.warn("AI generation failed for crash_analysis id={}: {}", crashAnalysisId, e.getMessage());
            markUnavailable(crashAnalysisId, e.getMessage(), isRegeneration, a);
        } catch (Exception e) {
            log.error("Unexpected AI generation error for crash_analysis id={}", crashAnalysisId, e);
            markUnavailable(crashAnalysisId, "unexpected error: " + e.getMessage(), isRegeneration, a);
        }
    }

    private void markPending(long crashAnalysisId, CrashAnalysis a) {
        jdbc.update("""
                        UPDATE analyzer.crash_analysis
                           SET ai_narration_status = ?,
                               ai_failure_reason = NULL
                         WHERE id = ?
                        """,
                STATUS_PENDING, crashAnalysisId);
        broadcaster.publish(a.getAppName(), "narrationStarted", Map.of(
                "crashId", a.getCrashEventId(),
                "analysisId", crashAnalysisId,
                "status", STATUS_PENDING
        ));
    }

    private void markUnavailable(long crashAnalysisId, String reason, boolean isRegeneration, CrashAnalysis a) {
        jdbc.update("""
                        UPDATE analyzer.crash_analysis
                           SET ai_narration_status = ?,
                               ai_failure_reason = ?,
                               ai_regenerate_count = ai_regenerate_count + ?
                         WHERE id = ?
                        """,
                STATUS_UNAVAILABLE, truncate(reason, 500),
                isRegeneration ? 1 : 0, crashAnalysisId);
        broadcaster.publish(a.getAppName(), "narrationFailed", Map.of(
                "crashId", a.getCrashEventId(),
                "analysisId", crashAnalysisId,
                "status", STATUS_UNAVAILABLE,
                "reason", truncate(reason, 200)
        ));
    }

    private Optional<CrashAnalysis> loadById(long id) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT * FROM analyzer.crash_analysis WHERE id = ?",
                    (rs, i) -> mapRow(rs), id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private CrashAnalysis mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        // Reuse the same shape as CrashAnalysisService — keep row mapping in one place
        // by delegating to a public helper there if the service is ever split further.
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

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
