package dev.filipnikolov.vector.github.commit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.github.client.GitHubClient;
import dev.filipnikolov.vector.github.encoding.GitHubUrlBuilder;
import dev.filipnikolov.vector.github.repo.RepoSlug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Fetches commit metadata from the GitHub REST API.
 */
public class CommitFetcher {

    private static final Logger log = LoggerFactory.getLogger(CommitFetcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GitHubClient client;

    public CommitFetcher(GitHubClient client) {
        this.client = client;
    }

    /**
     * Returns up to {@code limit} recent commits on {@code branch} since {@code since}.
     *
     * @param slug   repository owner/name
     * @param branch branch name (e.g. {@code main})
     * @param since  only commits at or after this instant
     * @param limit  maximum number of commits to return (used as {@code per_page})
     * @return list of commit metadata, newest-first; empty list on any error
     */
    public List<CommitMetadata> listRecent(RepoSlug slug, String branch, Instant since, int limit) {
        String url = GitHubUrlBuilder.commits(slug, branch, since, limit);
        try {
            String json = client.get(url, String.class);
            if (json == null) {
                return Collections.emptyList();
            }
            JsonNode array = MAPPER.readTree(json);
            if (!array.isArray()) {
                return Collections.emptyList();
            }
            List<CommitMetadata> result = new ArrayList<>(array.size());
            for (JsonNode node : array) {
                result.add(CommitMetadata.fromJsonNode(node));
            }
            return result;
        } catch (Exception e) {
            log.debug("CommitFetcher.listRecent failed for {}/{} branch={}: {}", slug.owner(), slug.name(), branch, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches a single commit by SHA.
     *
     * @param slug repository owner/name
     * @param sha  full or abbreviated commit SHA
     * @return the commit metadata, or {@link Optional#empty()} if not found or on error
     */
    public Optional<CommitMetadata> getOne(RepoSlug slug, String sha) {
        String url = GitHubUrlBuilder.commit(slug, sha);
        try {
            String json = client.get(url, String.class);
            if (json == null) {
                return Optional.empty();
            }
            JsonNode node = MAPPER.readTree(json);
            return Optional.of(CommitMetadata.fromJsonNode(node));
        } catch (Exception e) {
            log.debug("CommitFetcher.getOne failed for {}/{} sha={}: {}", slug.owner(), slug.name(), sha, e.getMessage());
            return Optional.empty();
        }
    }
}
