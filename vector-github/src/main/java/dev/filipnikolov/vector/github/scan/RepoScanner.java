package dev.filipnikolov.vector.github.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.github.client.GitHubException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Scans a repository's file tree via the GitHub REST API (no clone), fetching a capped set of
 * build manifests and detecting a root compose file as a signal-only field.
 */
public class RepoScanner {

    private static final String BASE_URL = "https://api.github.com";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> MANIFEST_FILENAMES = Set.of(
            "package.json", "go.mod", "pom.xml", "build.gradle", "build.gradle.kts",
            "requirements.txt", "pyproject.toml", "Dockerfile");
    private static final Set<String> IGNORED_SEGMENTS = Set.of(
            "node_modules", ".git", ".github", "vendor", "dist", "build", "target", "__pycache__");
    private static final Set<String> COMPOSE_FILENAMES = Set.of("docker-compose.yml", "compose.yaml");
    private static final int MAX_MANIFEST_FETCHES = 30;

    private final Supplier<String> tokenSupplier;
    private final RestClient restClient;

    public RepoScanner(Supplier<String> tokenSupplier, RestClient.Builder builder) {
        this.tokenSupplier = tokenSupplier;
        this.restClient = builder
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
    }

    public RepoScan scan(String owner, String repo, String ref) {
        String treeUrl = BASE_URL + "/repos/" + owner + "/" + repo + "/git/trees/" + ref + "?recursive=1";
        String json = restClient.get()
                .uri(treeUrl)
                .header("Authorization", "Bearer " + tokenSupplier.get())
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (!status.is2xxSuccessful()) {
                        throw new GitHubException("GitHub API returned HTTP " + status.value() + " for: " + treeUrl);
                    }
                    return new String(response.getBody().readAllBytes());
                });

        List<String> treePaths = new ArrayList<>();
        boolean truncated;
        try {
            JsonNode node = MAPPER.readTree(json);
            for (JsonNode entry : node.get("tree")) {
                treePaths.add(entry.get("path").asText());
            }
            truncated = node.has("truncated") && node.get("truncated").asBoolean();
        } catch (Exception e) {
            throw new GitHubException("Failed to parse tree response for: " + treeUrl, e);
        }

        List<ManifestFile> manifests = new ArrayList<>();
        Optional<String> composeYaml = Optional.empty();
        for (String path : treePaths) {
            if (containsIgnoredSegment(path)) {
                continue;
            }
            String filename = filenameOf(path);
            if (isRootCompose(path, filename)) {
                composeYaml = readContent(owner, repo, path, ref);
            }
            if (MANIFEST_FILENAMES.contains(filename) && manifests.size() < MAX_MANIFEST_FETCHES) {
                readContent(owner, repo, path, ref)
                        .ifPresent(content -> manifests.add(new ManifestFile(path, content)));
            }
        }

        return new RepoScan(treePaths, truncated, manifests, composeYaml);
    }

    private boolean containsIgnoredSegment(String path) {
        for (String segment : path.split("/")) {
            if (IGNORED_SEGMENTS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRootCompose(String path, String filename) {
        return !path.contains("/") && COMPOSE_FILENAMES.contains(filename);
    }

    private String filenameOf(String path) {
        int idx = path.lastIndexOf('/');
        return idx == -1 ? path : path.substring(idx + 1);
    }

    private Optional<String> readContent(String owner, String repo, String path, String ref) {
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + ref;
        String json = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + tokenSupplier.get())
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (status.value() == 404) {
                        return null;
                    }
                    if (!status.is2xxSuccessful()) {
                        throw new GitHubException("GitHub API returned HTTP " + status.value() + " for: " + url);
                    }
                    return new String(response.getBody().readAllBytes());
                });
        if (json == null) {
            return Optional.empty();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            String encoded = node.has("content") ? node.get("content").asText() : null;
            if (encoded == null) {
                return Optional.of("");
            }
            return Optional.of(new String(Base64.getDecoder().decode(encoded.replaceAll("\\s", ""))));
        } catch (Exception e) {
            throw new GitHubException("Failed to parse contents response for: " + owner + "/" + repo + "/" + path, e);
        }
    }
}
