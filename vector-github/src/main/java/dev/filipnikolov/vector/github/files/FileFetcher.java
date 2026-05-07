package dev.filipnikolov.vector.github.files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.github.client.GitHubClient;
import dev.filipnikolov.vector.github.commit.CommitMetadata;
import dev.filipnikolov.vector.github.encoding.GitHubUrlBuilder;
import dev.filipnikolov.vector.github.repo.RepoSlug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Fetches file content and file-commit history from the GitHub REST API.
 */
public class FileFetcher {

    private static final Logger log = LoggerFactory.getLogger(FileFetcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GitHubClient client;

    public FileFetcher(GitHubClient client) {
        this.client = client;
    }

    /**
     * Returns the decoded text content of a file at a specific commit ref.
     *
     * @param slug repository owner/name
     * @param sha  commit SHA or branch/tag name
     * @param path file path within the repository (e.g. {@code src/main/App.java})
     * @return file content, or {@link Optional#empty()} on 404 or error
     */
    public Optional<FileContent> contentAt(RepoSlug slug, String sha, String path) {
        String url = GitHubUrlBuilder.contents(slug, sha, path);
        try {
            String json = client.get(url, String.class);
            if (json == null) {
                return Optional.empty();
            }
            JsonNode node = MAPPER.readTree(json);

            String name    = node.path("name").asText(null);
            String filePath = node.path("path").asText(null);
            String encoded = node.path("content").asText(null);

            String content = "";
            if (encoded != null) {
                // GitHub returns base64 with embedded newlines — strip them before decoding
                String stripped = encoded.replaceAll("\\s", "");
                content = new String(Base64.getDecoder().decode(stripped));
            }

            return Optional.of(new FileContent(
                    name != null ? name : path,
                    filePath != null ? filePath : path,
                    content
            ));
        } catch (Exception e) {
            log.debug("FileFetcher.contentAt failed for {}/{} sha={} path={}: {}",
                    slug.owner(), slug.name(), sha, path, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns the commit history for a specific file path, newest-first.
     *
     * @param slug  repository owner/name
     * @param path  file path within the repository
     * @param limit maximum number of commits to return
     * @return list of commits touching the file; empty on error
     */
    public List<CommitMetadata> historyOf(RepoSlug slug, String path, int limit) {
        String url = GitHubUrlBuilder.commitsForPath(slug, path, limit);
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
            log.debug("FileFetcher.historyOf failed for {}/{} path={}: {}",
                    slug.owner(), slug.name(), path, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * File content with decoded text.
     *
     * @param name    file name (without path prefix)
     * @param path    full path within the repository
     * @param content decoded UTF-8 text content
     */
    public record FileContent(String name, String path, String content) {}
}
