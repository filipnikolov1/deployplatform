package dev.filipnikolov.vector.github.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.github.client.dto.RepoPublicKey;
import dev.filipnikolov.vector.github.client.dto.StackKind;
import dev.filipnikolov.vector.github.client.dto.WorkflowRun;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Write-capable {@link RestClient} wrapper for the GitHub REST API, mirroring {@link GitHubClient}
 * but authenticating each request with a fresh Bearer token resolved from the supplied
 * {@link Supplier} (a per-installation GitHub App token).
 */
public class GitHubAutomationClient {

    private static final String BASE_URL = "https://api.github.com";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Supplier<String> tokenSupplier;
    private final RestClient restClient;

    public GitHubAutomationClient(Supplier<String> tokenSupplier, RestClient.Builder builder) {
        this.tokenSupplier = tokenSupplier;
        this.restClient = builder
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
    }

    public void putFile(String owner, String repo, String path, byte[] content, String message, String branch) {
        String contentsUrl = BASE_URL + "/repos/" + owner + "/" + repo + "/contents/" + path;

        String existingSha = getExistingSha(contentsUrl, branch);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("content", Base64.getEncoder().encodeToString(content));
        if (existingSha != null) {
            body.put("sha", existingSha);
        }
        body.put("branch", branch);

        restClient.put()
                .uri(contentsUrl)
                .header("Authorization", "Bearer " + tokenSupplier.get())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private String getExistingSha(String contentsUrl, String branch) {
        try {
            String json = restClient.get()
                    .uri(contentsUrl + "?ref=" + branch)
                    .header("Authorization", "Bearer " + tokenSupplier.get())
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.value() == 404) {
                            return null;
                        }
                        if (!status.is2xxSuccessful()) {
                            throw new GitHubException("GitHub API returned HTTP " + status.value() + " for: " + contentsUrl);
                        }
                        return new String(response.getBody().readAllBytes());
                    });
            if (json == null) {
                return null;
            }
            JsonNode node = MAPPER.readTree(json);
            return node.has("sha") ? node.get("sha").asText() : null;
        } catch (GitHubException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubException("GitHub API call failed for: " + contentsUrl, e);
        }
    }

    public RepoPublicKey getRepoPublicKey(String owner, String repo) {
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/actions/secrets/public-key";
        String json = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + tokenSupplier.get())
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (!status.is2xxSuccessful()) {
                        throw new GitHubException("GitHub API returned HTTP " + status.value() + " for: " + url);
                    }
                    return new String(response.getBody().readAllBytes());
                });
        try {
            JsonNode node = MAPPER.readTree(json);
            return new RepoPublicKey(node.get("key_id").asText(), node.get("key").asText());
        } catch (GitHubException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubException("Failed to parse repo public key response for: " + url, e);
        }
    }

    public void putActionsSecret(String owner, String repo, String name, String plaintext) {
        RepoPublicKey key = getRepoPublicKey(owner, repo);
        String encryptedValue = SealedBox.seal(plaintext, key.key());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("encrypted_value", encryptedValue);
        body.put("key_id", key.keyId());

        restClient.put()
                .uri(BASE_URL + "/repos/" + owner + "/" + repo + "/actions/secrets/" + name)
                .header("Authorization", "Bearer " + tokenSupplier.get())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void dispatchWorkflow(String owner, String repo, String workflowFile, String ref) {
        Map<String, Object> body = Map.of("ref", ref);

        restClient.post()
                .uri(BASE_URL + "/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/dispatches")
                .header("Authorization", "Bearer " + tokenSupplier.get())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public List<WorkflowRun> recentRuns(String owner, String repo, String workflowFile) {
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs";
        String json = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + tokenSupplier.get())
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (!status.is2xxSuccessful()) {
                        throw new GitHubException("GitHub API returned HTTP " + status.value() + " for: " + url);
                    }
                    return new String(response.getBody().readAllBytes());
                });
        try {
            JsonNode node = MAPPER.readTree(json);
            List<WorkflowRun> runs = new ArrayList<>();
            for (JsonNode run : node.get("workflow_runs")) {
                runs.add(new WorkflowRun(
                        run.get("id").asLong(),
                        run.get("status").asText(),
                        run.hasNonNull("conclusion") ? run.get("conclusion").asText() : null,
                        run.get("html_url").asText()));
            }
            return runs;
        } catch (GitHubException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubException("Failed to parse workflow runs response for: " + url, e);
        }
    }

    public List<dev.filipnikolov.vector.github.client.dto.RunJob> listRunJobs(String owner, String repo, long runId) {
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/jobs";
        String json = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + tokenSupplier.get())
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (!status.is2xxSuccessful()) {
                        throw new GitHubException("GitHub API returned HTTP " + status.value() + " for: " + url);
                    }
                    return new String(response.getBody().readAllBytes());
                });
        try {
            JsonNode node = MAPPER.readTree(json);
            List<dev.filipnikolov.vector.github.client.dto.RunJob> jobs = new ArrayList<>();
            for (JsonNode job : node.get("jobs")) {
                jobs.add(new dev.filipnikolov.vector.github.client.dto.RunJob(
                        job.get("id").asLong(),
                        job.get("name").asText(),
                        job.get("status").asText(),
                        job.hasNonNull("conclusion") ? job.get("conclusion").asText() : null,
                        job.get("html_url").asText()));
            }
            return jobs;
        } catch (GitHubException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubException("Failed to parse run jobs response for: " + url, e);
        }
    }

    public StackKind detectStack(String owner, String repo, String ref) {
        if (contentExists(owner, repo, "go.mod", ref)) {
            return StackKind.go;
        }
        String packageJson = readFile(owner, repo, "package.json", ref);
        if (packageJson != null) {
            return packageJson.contains("\"next\"") ? StackKind.nextjs : StackKind.node;
        }
        if (contentExists(owner, repo, "pom.xml", ref)) {
            return StackKind.springboot;
        }
        if (contentExists(owner, repo, "requirements.txt", ref)) {
            return StackKind.python;
        }
        // Dockerfile present (or absent) both resolve to custom — checked to keep the read order
        // faithful to the detection priority (a 404 here still means "custom", not "unknown").
        contentExists(owner, repo, "Dockerfile", ref);
        return StackKind.custom;
    }

    private boolean contentExists(String owner, String repo, String path, String ref) {
        return readRawContent(owner, repo, path, ref) != null;
    }

    /**
     * Reads and decodes a repo file's content via the contents API, or {@code null} if it
     * doesn't exist at {@code ref}.
     */
    public String readFile(String owner, String repo, String path, String ref) {
        String json = readRawContent(owner, repo, path, ref);
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            String encoded = node.has("content") ? node.get("content").asText() : null;
            if (encoded == null) {
                return "";
            }
            return new String(Base64.getDecoder().decode(encoded.replaceAll("\\s", "")));
        } catch (Exception e) {
            throw new GitHubException("Failed to parse contents response for: " + owner + "/" + repo + "/" + path, e);
        }
    }

    private String readRawContent(String owner, String repo, String path, String ref) {
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + ref;
        return restClient.get()
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
    }
}
