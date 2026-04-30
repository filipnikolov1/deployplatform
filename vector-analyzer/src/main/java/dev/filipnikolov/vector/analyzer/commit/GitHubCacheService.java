package dev.filipnikolov.vector.analyzer.commit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GitHubCacheService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCacheService.class);

    private final JdbcTemplate jdbc;
    private final String token;
    private final RestClient client;

    public GitHubCacheService(JdbcTemplate jdbc,
                               @Value("${github.token:}") String token) {
        this.jdbc = jdbc;
        this.token = token;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));

        this.client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
    }

    /**
     * Returns diff JSON between baseSha and headSha, using cache. Returns null if unavailable.
     */
    @SuppressWarnings("unchecked")
    public String fetchDiff(String slug, String baseSha, String headSha) {
        if (token == null || token.isBlank()) return null;

        // Check cache
        List<Map<String, Object>> cached = jdbc.queryForList(
                "SELECT diff_json FROM analyzer.diff_cache WHERE repo_full_name = ? AND base_sha = ? AND head_sha = ?",
                slug, baseSha, headSha);
        if (!cached.isEmpty()) {
            return (String) cached.get(0).get("diff_json");
        }

        try {
            String url = "/repos/" + slug + "/compare/" + baseSha + "..." + headSha;
            Map<?, ?> body = client.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(Map.class);
            if (body == null) return null;

            // Store the relevant subset as JSON string via simple serialisation
            String diffJson = toJson(body);
            jdbc.update("""
                    INSERT INTO analyzer.diff_cache (repo_full_name, base_sha, head_sha, diff_json, cached_at)
                    VALUES (?, ?, ?, ?, NOW())
                    ON CONFLICT (repo_full_name, base_sha, head_sha) DO UPDATE SET diff_json = EXCLUDED.diff_json, cached_at = NOW()
                    """, slug, baseSha, headSha, diffJson);
            return diffJson;
        } catch (Exception e) {
            log.debug("GitHub diff fetch failed for {}/{} → {}: {}", slug, baseSha, headSha, e.getMessage());
            return null;
        }
    }

    /**
     * Returns file content at a specific SHA. Returns null if unavailable.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchFileContent(String slug, String sha, String path) {
        if (token == null || token.isBlank()) return null;

        try {
            String url = "/repos/" + slug + "/contents/" + path + "?ref=" + sha;
            Map<?, ?> body = client.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(Map.class);
            if (body == null) return null;

            String encoded = (String) body.get("content");
            String name    = (String) body.get("name");
            String filePath = (String) body.get("path");

            String content = "";
            if (encoded != null) {
                // GitHub returns base64 with newlines
                content = new String(Base64.getMimeDecoder().decode(encoded));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", true);
            result.put("content", content);
            result.put("name", name != null ? name : path);
            result.put("path", filePath != null ? filePath : path);
            return result;
        } catch (Exception e) {
            log.debug("GitHub file fetch failed for {}/{} path={}: {}", slug, sha, path, e.getMessage());
            return null;
        }
    }

    /** Minimal JSON serialiser sufficient for GitHub API response maps. */
    private String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            return sb.append("]").toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(e.getKey().toString())).append(":").append(toJson(e.getValue()));
            }
            return sb.append("}").toString();
        }
        return "\"" + value.toString().replace("\"", "\\\"") + "\"";
    }
}
