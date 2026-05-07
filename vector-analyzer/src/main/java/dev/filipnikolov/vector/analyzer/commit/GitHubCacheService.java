package dev.filipnikolov.vector.analyzer.commit;

import dev.filipnikolov.vector.github.client.GitHubClient;
import dev.filipnikolov.vector.github.diff.DiffFetcher;
import dev.filipnikolov.vector.github.files.FileFetcher;
import dev.filipnikolov.vector.github.files.FileFetcher.FileContent;
import dev.filipnikolov.vector.github.repo.RepoSlug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DB-cache layer for GitHub data.
 *
 * <p>Handles read/write to {@code analyzer.diff_cache}; delegates actual HTTP calls
 * to {@link DiffFetcher} and {@link FileFetcher} from the {@code vector-github} module.
 */
@Service
public class GitHubCacheService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCacheService.class);

    private final JdbcTemplate jdbc;
    private final String token;
    private final DiffFetcher diffFetcher;
    private final FileFetcher fileFetcher;

    public GitHubCacheService(JdbcTemplate jdbc,
                               @Value("${github.token:}") String token) {
        this.jdbc = jdbc;
        this.token = token;

        GitHubClient client = new GitHubClient(token);
        this.diffFetcher = new DiffFetcher(client);
        this.fileFetcher = new FileFetcher(client);
    }

    /**
     * Returns diff JSON between baseSha and headSha, using the DB cache.
     * Returns {@code null} if the token is absent or the diff is unavailable.
     */
    public String fetchDiff(String slug, String baseSha, String headSha) {
        if (token == null || token.isBlank()) return null;

        // Check cache first
        List<Map<String, Object>> cached = jdbc.queryForList(
                "SELECT diff_json FROM analyzer.diff_cache WHERE repo_full_name = ? AND base_sha = ? AND head_sha = ?",
                slug, baseSha, headSha);
        if (!cached.isEmpty()) {
            return (String) cached.get(0).get("diff_json");
        }

        RepoSlug repoSlug = parseSlug(slug);
        if (repoSlug == null) return null;

        Optional<String> fetched = diffFetcher.compare(repoSlug, baseSha, headSha);
        if (fetched.isEmpty()) return null;

        String diffJson = fetched.get();
        try {
            jdbc.update("""
                    INSERT INTO analyzer.diff_cache (repo_full_name, base_sha, head_sha, diff_json, cached_at)
                    VALUES (?, ?, ?, ?, NOW())
                    ON CONFLICT (repo_full_name, base_sha, head_sha) DO UPDATE SET diff_json = EXCLUDED.diff_json, cached_at = NOW()
                    """, slug, baseSha, headSha, diffJson);
        } catch (Exception e) {
            log.debug("Failed to cache diff for {}/{} → {}: {}", slug, baseSha, headSha, e.getMessage());
        }
        return diffJson;
    }

    /**
     * Returns the last {@code limit} commits that touched {@code path}.
     * Returns {@code null} if unavailable. Uncached — hits GitHub directly each call.
     */
    public List<Map<String, Object>> fetchFileCommits(String slug, String path, int limit) {
        if (token == null || token.isBlank()) return null;

        RepoSlug repoSlug = parseSlug(slug);
        if (repoSlug == null) return null;

        List<dev.filipnikolov.vector.github.commit.CommitMetadata> commits =
                fileFetcher.historyOf(repoSlug, path, limit);
        if (commits.isEmpty()) return null;

        // Convert to the Map<String, Object> shape that CommitService expects
        List<Map<String, Object>> result = new java.util.ArrayList<>(commits.size());
        for (dev.filipnikolov.vector.github.commit.CommitMetadata c : commits) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sha", c.sha());
            Map<String, Object> commitNode = new LinkedHashMap<>();
            Map<String, Object> authorNode = new LinkedHashMap<>();
            authorNode.put("name", c.author());
            authorNode.put("date", c.authoredAt() != null ? c.authoredAt().toString() : null);
            commitNode.put("author", authorNode);
            commitNode.put("message", c.message());
            entry.put("commit", commitNode);
            result.add(entry);
        }
        return result;
    }

    /**
     * Returns file content at a specific SHA. Returns {@code null} if unavailable.
     */
    public Map<String, Object> fetchFileContent(String slug, String sha, String path) {
        if (token == null || token.isBlank()) return null;

        RepoSlug repoSlug = parseSlug(slug);
        if (repoSlug == null) return null;

        Optional<FileContent> fetched = fileFetcher.contentAt(repoSlug, sha, path);
        if (fetched.isEmpty()) return null;

        FileContent fc = fetched.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("content", fc.content());
        result.put("name", fc.name());
        result.put("path", fc.path());
        return result;
    }

    // ---- helpers ----

    private static RepoSlug parseSlug(String slug) {
        if (slug == null || slug.isBlank()) return null;
        String[] parts = slug.split("/", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) return null;
        return new RepoSlug(parts[0], parts[1]);
    }
}
