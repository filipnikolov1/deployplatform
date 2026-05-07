package dev.filipnikolov.vector.github.diff;

import dev.filipnikolov.vector.github.client.GitHubClient;
import dev.filipnikolov.vector.github.encoding.GitHubUrlBuilder;
import dev.filipnikolov.vector.github.repo.RepoSlug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Fetches commit comparison (diff) data from the GitHub REST API.
 *
 * <p>The raw JSON string is returned intentionally so callers can store it in their
 * diff cache without re-serialising — this preserves the existing {@code analyzer.diff_cache}
 * contract exactly.
 */
public class DiffFetcher {

    private static final Logger log = LoggerFactory.getLogger(DiffFetcher.class);

    private final GitHubClient client;

    public DiffFetcher(GitHubClient client) {
        this.client = client;
    }

    /**
     * Fetches the comparison JSON between {@code baseSha} and {@code headSha}.
     *
     * @param slug    repository owner/name
     * @param baseSha base commit SHA (the older commit)
     * @param headSha head commit SHA (the newer commit)
     * @return raw GitHub compare JSON, or {@link Optional#empty()} on 404 or error
     */
    public Optional<String> compare(RepoSlug slug, String baseSha, String headSha) {
        String url = GitHubUrlBuilder.compare(slug, baseSha, headSha);
        try {
            String json = client.get(url, String.class);
            return Optional.ofNullable(json);
        } catch (Exception e) {
            log.debug("DiffFetcher.compare failed for {}/{} {}...{}: {}",
                    slug.owner(), slug.name(), baseSha, headSha, e.getMessage());
            return Optional.empty();
        }
    }
}
