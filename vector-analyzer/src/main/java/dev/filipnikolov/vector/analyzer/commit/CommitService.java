package dev.filipnikolov.vector.analyzer.commit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommitService {

    private static final Logger log = LoggerFactory.getLogger(CommitService.class);

    private final JdbcTemplate jdbc;
    private final GitHubCacheService github;

    public CommitService(JdbcTemplate jdbc, GitHubCacheService github) {
        this.jdbc = jdbc;
        this.github = github;
    }

    /** Commit metadata + deployment IDs where this SHA ran for appName. */
    public Map<String, Object> getCommitDetail(String appName, String sha) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sha", sha);

        // Deployment IDs where this SHA ran
        List<Map<String, Object>> deps = jdbc.queryForList(
                "SELECT id FROM public.deployment WHERE app_name = ? AND commit_sha = ? AND deleted_at IS NULL",
                appName, sha);
        List<Long> depIds = new ArrayList<>();
        for (Map<String, Object> row : deps) {
            depIds.add(((Number) row.get("id")).longValue());
        }
        result.put("deploymentIds", depIds);

        // Repo slug for this app
        String repoSlug = resolveRepoSlug(appName);
        result.put("repoSlug", repoSlug != null ? repoSlug : "");

        // Commit metadata from cache
        if (repoSlug != null && !repoSlug.isBlank()) {
            List<Map<String, Object>> cached = jdbc.queryForList(
                    "SELECT author, authored_at, message FROM analyzer.commit_cache WHERE repo_full_name = ? AND sha = ?",
                    repoSlug, sha);
            if (!cached.isEmpty()) {
                Map<String, Object> row = cached.get(0);
                result.put("author", row.get("author"));
                result.put("authoredAt", row.get("authored_at") != null ? row.get("authored_at").toString() : null);
                result.put("message", row.get("message"));
            }
        }

        // Also pull message from timeline COMMIT event metadata if not in commit_cache
        if (!result.containsKey("message") || result.get("message") == null) {
            List<Map<String, Object>> te = jdbc.queryForList(
                    "SELECT metadata_json FROM analyzer.timeline_event WHERE app_name = ? AND commit_sha = ? AND event_type = 'COMMIT' LIMIT 1",
                    appName, sha);
            if (!te.isEmpty()) {
                String meta = (String) te.get(0).get("metadata_json");
                if (meta != null) {
                    // Extract message field — simple approach, avoid pulling in Jackson here
                    result.put("metadataJson", meta);
                }
            }
        }

        return result;
    }

    /** Log entries for deployment windows where this SHA ran. Capped at 5000 lines. */
    public List<Map<String, Object>> getCommitLogs(String appName, String sha) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT id, timestamp, stream, line
                FROM analyzer.log_entry
                WHERE app_name = ? AND commit_sha = ?
                ORDER BY timestamp ASC
                LIMIT 5000
                """,
                appName, sha);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", ((Number) row.get("id")).longValue());
            entry.put("timestamp", row.get("timestamp") != null ? row.get("timestamp").toString() : null);
            entry.put("stream", row.get("stream"));
            entry.put("line", row.get("line"));
            result.add(entry);
        }
        return result;
    }

    /** Diff of sha vs its predecessor, fetched from GitHub and cached. */
    public Map<String, Object> getCommitDiff(String appName, String sha) {
        String slug = resolveRepoSlug(appName);
        if (slug == null || slug.isBlank()) {
            return Map.of("available", false, "reason", "no repo configured");
        }

        // Find the previous DEPLOY event's commit SHA for this app
        List<Map<String, Object>> prior = jdbc.queryForList(
                """
                SELECT commit_sha FROM analyzer.timeline_event
                WHERE app_name = ? AND event_type = 'DEPLOY' AND commit_sha IS NOT NULL AND commit_sha != ?
                  AND occurred_at < (
                      SELECT MIN(occurred_at) FROM analyzer.timeline_event
                      WHERE app_name = ? AND commit_sha = ? AND event_type = 'DEPLOY'
                  )
                ORDER BY occurred_at DESC
                LIMIT 1
                """,
                appName, sha, appName, sha);

        if (prior.isEmpty()) {
            return Map.of("available", false, "reason", "no previous commit found");
        }
        String baseSha = (String) prior.get(0).get("commit_sha");

        String diffJson = github.fetchDiff(slug, baseSha, sha);
        if (diffJson == null) {
            return Map.of("available", false, "reason", "diff unavailable");
        }
        return Map.of("available", true, "diffJson", diffJson, "baseSha", baseSha, "headSha", sha);
    }

    /** File contents at a specific SHA from GitHub. */
    public Map<String, Object> getFileAtCommit(String appName, String sha, String path) {
        String slug = resolveRepoSlug(appName);
        if (slug == null || slug.isBlank()) {
            return Map.of("available", false, "reason", "no repo configured");
        }
        Map<String, Object> result = github.fetchFileContent(slug, sha, path);
        if (result == null) {
            return Map.of("available", false, "reason", "file unavailable");
        }
        return result;
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
}
