package dev.filipnikolov.vector.github.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * Thin {@link RestClient} wrapper for the GitHub REST API.
 *
 * <p>Construct with a personal-access token (or an empty/blank string for unauthenticated
 * requests). This class is intentionally not a Spring bean — callers are responsible for
 * injecting the token value so the wrapper can be reused from any context.
 */
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

    private final RestClient restClient;

    public GitHubClient(String token) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Accept", "application/vnd.github+json");

        if (token != null && !token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
        }

        this.restClient = builder.build();
    }

    /**
     * Performs a GET request and deserialises the response body.
     *
     * @param url          absolute URL (including query string)
     * @param responseType target type
     * @param <T>          body type
     * @return parsed body, or {@code null} on HTTP 404
     * @throws GitHubRateLimitException when HTTP 403 with {@code X-RateLimit-Remaining: 0}
     * @throws GitHubException          on any other HTTP error
     */
    public <T> T get(String url, Class<T> responseType) {
        try {
            return restClient.get()
                    .uri(url)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        int code = status.value();

                        if (code == 404) {
                            return null;
                        }

                        if (code == 403) {
                            String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
                            if ("0".equals(remaining)) {
                                throw new GitHubRateLimitException(
                                        "GitHub rate limit exhausted (HTTP 403, X-RateLimit-Remaining: 0) for: " + url);
                            }
                            String body = readExcerpt(response.getBody());
                            throw new GitHubException("GitHub API returned HTTP 403 for: " + url + " — " + body);
                        }

                        if (!status.is2xxSuccessful()) {
                            String body = readExcerpt(response.getBody());
                            throw new GitHubException("GitHub API returned HTTP " + code + " for: " + url + " — " + body);
                        }

                        // 2xx — decode the body
                        if (responseType == String.class) {
                            @SuppressWarnings("unchecked")
                            T result = (T) new String(response.getBody().readAllBytes());
                            return result;
                        }
                        // For other types, use Jackson via the response body converter
                        // (RestClient exchange doesn't provide a converter context, so callers
                        //  should use String.class and parse externally — see CommitFetcher/DiffFetcher)
                        throw new GitHubException("GitHubClient.get only supports String.class as responseType; got: " + responseType.getName());
                    });
        } catch (GitHubException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubException("GitHub API call failed for: " + url, e);
        }
    }

    private static String readExcerpt(InputStream body) {
        if (body == null) return "<no body>";
        try {
            byte[] bytes = body.readNBytes(512);
            return new String(bytes);
        } catch (IOException ex) {
            return "<unreadable>";
        }
    }
}
