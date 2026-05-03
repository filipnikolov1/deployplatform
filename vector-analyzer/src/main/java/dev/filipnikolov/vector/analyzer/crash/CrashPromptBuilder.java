package dev.filipnikolov.vector.analyzer.crash;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Builds the prompt sent to the LLM when generating a crash narration.
 * The output is a single string containing both the system rules and the
 * structured user evidence (Gemini's REST API takes a single text field).
 *
 * The rules here are load-bearing for narration quality — see prompt iteration
 * notes in the Phase 6 commit history before tuning.
 */
final class CrashPromptBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Cap on per-evidence-item content size we forward to the LLM. Long log
     * lines and full diff JSONs blow up token usage without improving the
     * narration; the LLM only needs enough context to cite specific items.
     */
    private static final int MAX_LOG_LINE_CHARS = 400;
    private static final int MAX_DIFF_CHARS = 4_000;

    private CrashPromptBuilder() {}

    static String build(CrashAnalysis analysis) {
        List<Map<String, Object>> evidence = parseEvidence(analysis.getEvidenceJson());
        Map<String, Object> signals = parseObject(analysis.getSignalsJson());

        StringBuilder sb = new StringBuilder(8_192);
        sb.append(systemRules());
        sb.append("\n\n## Crash context\n");
        sb.append(contextLine(analysis, signals));
        sb.append("\n\n## Evidence\n");
        sb.append("Each item is tagged with an id like [1], [2]. Cite items inline by writing the marker exactly as shown.\n\n");
        for (Map<String, Object> item : evidence) {
            sb.append(formatEvidenceItem(item));
        }
        sb.append("\n## Output format\n");
        sb.append("Write 3–6 short paragraphs in markdown. No headings. Cite at least 2 evidence items. ");
        sb.append("Do not suggest specific code fixes; describe what likely happened, not how to fix it.\n");
        return sb.toString();
    }

    private static String systemRules() {
        return """
                You are an SRE narrating a production crash to a developer who needs context fast.
                Write a concise, calibrated explanation grounded in the evidence below.

                Rules — these are not optional:
                1. EVERY factual claim must end with a citation marker like [1] or [2] referencing the evidence id.
                   If you can't cite a claim, drop it. No outside knowledge, no guessing about code that isn't shown.
                2. Use uncertainty language: "likely", "possibly", "appears to be", "suggests". Never say
                   "the cause is" or "this was caused by". You are reasoning from limited evidence.
                3. Do NOT propose code fixes, refactors, or specific patches. The reader will write the fix; you
                   describe the likely failure mode and the chain of events.
                4. Calibrate confidence against the time_since_deploy_minutes signal:
                   - If < 60 minutes: the recent deploy is a strong suspect — say so, with caveats.
                   - If 60–1440 minutes: the deploy is one of several plausible causes — be even-handed.
                   - If > 1440 minutes (>24h): the deploy is unlikely to be the proximate cause; lead with
                     "the crash happened long after the last deploy, so changes from that deploy are unlikely
                     to be the proximate cause" (or equivalent), then describe what the logs show.
                5. If crash_count_for_commit is > 1, mention this is a recurring failure under this version.
                6. Output is markdown. No section headings, no bullet lists unless they genuinely aid clarity.
                   Plain prose paragraphs. Keep it tight — a developer is paged.
                """;
    }

    private static String contextLine(CrashAnalysis analysis, Map<String, Object> signals) {
        Object timeSince = signals.get("timeSinceDeployMinutes");
        Object crashCount = signals.get("crashCountForCommit");
        Object lastDeployedAt = signals.get("lastDeployedAt");
        Object crashedAt = signals.get("crashedAt");
        StringBuilder s = new StringBuilder();
        s.append("- app: ").append(analysis.getAppName()).append('\n');
        s.append("- suspect_commit: ").append(orNone(analysis.getSuspectCommitSha())).append('\n');
        s.append("- last_good_commit: ").append(orNone(analysis.getLastGoodCommitSha())).append('\n');
        s.append("- suspect_file: ").append(orNone(analysis.getSuspectFilePath()));
        if (analysis.getSuspectLine() != null) s.append(":").append(analysis.getSuspectLine());
        s.append('\n');
        s.append("- crashed_at: ").append(orNone(crashedAt)).append('\n');
        s.append("- last_deployed_at: ").append(orNone(lastDeployedAt)).append('\n');
        s.append("- time_since_deploy_minutes: ").append(orNone(timeSince)).append('\n');
        s.append("- crash_count_for_commit: ").append(orNone(crashCount)).append('\n');
        return s.toString();
    }

    private static String formatEvidenceItem(Map<String, Object> item) {
        Object id = item.get("id");
        String type = String.valueOf(item.get("type"));
        String source = item.get("source") != null ? String.valueOf(item.get("source")) : "";
        StringBuilder s = new StringBuilder();
        s.append('[').append(id).append("] ").append(type);
        if (!source.isEmpty()) s.append(" · ").append(source);
        if (item.get("timestamp") != null) s.append(" · ").append(item.get("timestamp"));
        s.append('\n');

        Object content = item.get("content");
        switch (type) {
            case "log" -> s.append(truncate(String.valueOf(content), MAX_LOG_LINE_CHARS)).append('\n');
            case "diff" -> {
                String diff = content instanceof String str ? str : safeJson(content);
                s.append("```\n").append(truncate(diff, MAX_DIFF_CHARS)).append("\n```\n");
            }
            case "commit" -> {
                if (content instanceof Map<?, ?> m) {
                    Object msg = m.get("message");
                    Object author = m.get("author");
                    if (author != null) s.append("author: ").append(author).append('\n');
                    if (msg != null) s.append("message: ").append(truncate(String.valueOf(msg), 800)).append('\n');
                } else {
                    s.append(safeJson(content)).append('\n');
                }
            }
            default -> s.append(safeJson(content)).append('\n');
        }
        s.append('\n');
        return s.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…[truncated]";
    }

    private static String orNone(Object v) {
        return v == null ? "(none)" : v.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseEvidence(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String safeJson(Object v) {
        try { return MAPPER.writeValueAsString(v); }
        catch (Exception e) { return String.valueOf(v); }
    }
}
