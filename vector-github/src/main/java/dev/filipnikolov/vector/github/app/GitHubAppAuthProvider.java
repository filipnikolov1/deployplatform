package dev.filipnikolov.vector.github.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;

import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Exchanges GitHub App JWTs for per-installation access tokens, caching them until shortly
 * before expiry.
 */
public class GitHubAppAuthProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);
    private static final String BASE_URL = "https://api.github.com";

    private final String appId;
    private final PrivateKey key;
    private final RestClient restClient;
    private final ConcurrentHashMap<Long, InstallationToken> cache = new ConcurrentHashMap<>();
    private volatile AppInfo appInfo;

    public GitHubAppAuthProvider(String appId, PrivateKey key, RestClient.Builder builder) {
        this.appId = appId;
        this.key = key;
        this.restClient = builder.defaultHeader("Accept", "application/vnd.github+json").build();
    }

    /**
     * Returns a cached installation token, refetching when it is missing or within
     * {@link #REFRESH_MARGIN} of expiry.
     */
    public String tokenFor(long installationId) {
        InstallationToken cached = cache.get(installationId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plus(REFRESH_MARGIN))) {
            return cached.token();
        }
        InstallationToken fetched = fetchInstallationToken(installationId);
        cache.put(installationId, fetched);
        return fetched.token();
    }

    /**
     * Returns a {@link Supplier} that lazily resolves {@link #tokenFor(long)} for the given
     * installation, suitable for wiring into {@link dev.filipnikolov.vector.github.client.GitHubClient}
     * consumers that expect a token supplier.
     */
    public Supplier<String> tokenSupplier(long installationId) {
        return () -> tokenFor(installationId);
    }

    /**
     * Returns App metadata from {@code GET /app}, cached forever.
     */
    public AppInfo appInfo() {
        AppInfo current = appInfo;
        if (current != null) {
            return current;
        }
        String jwt = GitHubAppJwt.create(appId, key, Instant.now());
        String json = restClient.get()
                .uri(BASE_URL + "/app")
                .header("Authorization", "Bearer " + jwt)
                .retrieve()
                .body(String.class);
        AppInfo fetched = parseAppInfo(json);
        appInfo = fetched;
        return fetched;
    }

    private InstallationToken fetchInstallationToken(long installationId) {
        String jwt = GitHubAppJwt.create(appId, key, Instant.now());
        String json = restClient.post()
                .uri(BASE_URL + "/app/installations/" + installationId + "/access_tokens")
                .header("Authorization", "Bearer " + jwt)
                .retrieve()
                .body(String.class);
        try {
            JsonNode node = MAPPER.readTree(json);
            String token = node.get("token").asText();
            Instant expiresAt = Instant.parse(node.get("expires_at").asText());
            return new InstallationToken(token, expiresAt);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse installation token response", e);
        }
    }

    private AppInfo parseAppInfo(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            String slug = node.get("slug").asText();
            String ownerLogin = node.get("owner").get("login").asText();
            return new AppInfo(slug, ownerLogin);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse app info response", e);
        }
    }

    /**
     * GitHub App metadata from {@code GET /app}.
     */
    public record AppInfo(String slug, String ownerLogin) {
    }
}
