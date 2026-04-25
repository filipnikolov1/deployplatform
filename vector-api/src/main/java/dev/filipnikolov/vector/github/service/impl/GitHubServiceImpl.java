package dev.filipnikolov.vector.github.service.impl;

import dev.filipnikolov.vector.github.dto.CommitsAhead;
import dev.filipnikolov.vector.github.service.GitHubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GitHubServiceImpl implements GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubServiceImpl.class);
    private static final long CACHE_TTL_MS = 60_000;

    private final RestClient client;
    private final String token;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(CommitsAhead value, long expiresAt) {}

    public GitHubServiceImpl(@Value("${github.token:}") String token) {
        this.token = token;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
    }

    @Override
    public CommitsAhead compare(String repoUrl, String base, String head) {
        String key = repoUrl + "|" + base + "|" + head;
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.expiresAt > now) {
            return entry.value;
        }
        CommitsAhead fresh = doCompare(repoUrl, base, head);
        cache.put(key, new CacheEntry(fresh, now + CACHE_TTL_MS));
        return fresh;
    }

    @SuppressWarnings("unchecked")
    private CommitsAhead doCompare(String repoUrl, String base, String head) {
        String slug = parseOwnerRepo(repoUrl);
        String compareUrl = "https://github.com/" + slug + "/compare/" + base + "..." + head;

        if (token == null || token.isBlank()) {
            return new CommitsAhead(null, List.of(), compareUrl);
        }

        String[] ownerRepo = slug.split("/", 2);
        if (ownerRepo.length != 2) {
            return new CommitsAhead(null, List.of(), compareUrl);
        }

        try {
            Map<String, Object> resp = client.get()
                    .uri("/repos/{owner}/{repo}/compare/{base}...{head}",
                            ownerRepo[0], ownerRepo[1], base, head)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) {
                return new CommitsAhead(null, List.of(), compareUrl);
            }

            int count = ((Number) resp.get("ahead_by")).intValue();
            List<CommitsAhead.CommitInfo> commits = ((List<Map<String, Object>>) resp.get("commits")).stream()
                    .map(c -> {
                        Map<String, Object> commit = (Map<String, Object>) c.get("commit");
                        Map<String, Object> author = (Map<String, Object>) commit.get("author");
                        return new CommitsAhead.CommitInfo(
                                (String) c.get("sha"),
                                (String) commit.get("message"),
                                (String) author.get("name"),
                                (String) author.get("date"),
                                (String) c.get("html_url"));
                    })
                    .toList();
            return new CommitsAhead(count, commits, compareUrl);
        } catch (Exception e) {
            log.debug("GitHub compare failed for {} {}..{}: {}", slug, base, head, e.getMessage());
            return new CommitsAhead(null, List.of(), compareUrl);
        }
    }

    private static String parseOwnerRepo(String repoUrl) {
        if (repoUrl == null) return "";
        return repoUrl
                .replaceFirst("^https?://github.com/", "")
                .replaceFirst("\\.git$", "");
    }
}
