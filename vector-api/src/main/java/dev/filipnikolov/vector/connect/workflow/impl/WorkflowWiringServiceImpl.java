package dev.filipnikolov.vector.connect.workflow.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.connect.dto.ConnectRequest;
import dev.filipnikolov.vector.connect.workflow.BuildpackWorkflowRenderer;
import dev.filipnikolov.vector.connect.workflow.ModuleJob;
import dev.filipnikolov.vector.connect.workflow.WiringResult;
import dev.filipnikolov.vector.connect.workflow.WorkflowWiringService;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.github.client.GitHubAutomationClient;
import dev.filipnikolov.vector.github.client.GitHubException;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.model.WorkflowMode;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import dev.filipnikolov.vector.githubapp.service.GitHubAutomationService;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
public class WorkflowWiringServiceImpl implements WorkflowWiringService {

    private static final String BASE_URL = "https://api.github.com";
    private static final String WORKFLOW_PATH = ".github/workflows/vector-deploy.yml";
    private static final String WORKFLOW_FILE = "vector-deploy.yml";
    private static final int TEMPLATE_VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CUSTOM_SNIPPET = """
            # Add this to your existing workflow to stay event-driven:
            permissions:
              contents: read
              packages: write
            steps:
              - name: Log in to GHCR
                run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
              - name: Build and push
                run: |
                  docker build -t image:${{ github.sha }} .
                  docker push image:${{ github.sha }}
            """;

    private final GitHubAutomationService automationService;
    private final GitHubAppConfigService configService;
    private final GitHubRepoRepository repoRepository;
    private final DeploymentEventService deploymentEventService;
    private final BuildpackWorkflowRenderer renderer;
    private final RestClient restClient;

    public WorkflowWiringServiceImpl(GitHubAutomationService automationService,
                                      GitHubAppConfigService configService,
                                      GitHubRepoRepository repoRepository,
                                      DeploymentEventService deploymentEventService,
                                      BuildpackWorkflowRenderer renderer,
                                      RestClient.Builder restClientBuilder) {
        this.automationService = automationService;
        this.configService = configService;
        this.repoRepository = repoRepository;
        this.deploymentEventService = deploymentEventService;
        this.renderer = renderer;
        this.restClient = restClientBuilder
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
    }

    @Override
    public WiringResult wire(GitHubRepo repo, ConnectRequest req, List<ModuleJob> jobs) {
        String[] parts = repo.getFullName().split("/", 2);
        String owner = parts[0];
        String name = parts[1];
        String defaultBranch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";

        String appSlug = configService.resolve().map(GitHubAppConfigService.AppCredentials::appSlug).orElse(null);
        String existingContent = readExistingWorkflow(owner, name, defaultBranch, repo.getInstallationId());

        WorkflowMode effectiveMode = req.workflowMode();
        if (existingContent != null && !isOurs(existingContent, appSlug)) {
            effectiveMode = WorkflowMode.CUSTOM;
        }

        if (effectiveMode == WorkflowMode.CUSTOM) {
            persist(repo, WorkflowMode.CUSTOM, null, null);
            return new WiringResult(WorkflowMode.CUSTOM, WORKFLOW_PATH, CUSTOM_SNIPPET);
        }

        BuildpackWorkflowRenderer.RenderSpec spec = new BuildpackWorkflowRenderer.RenderSpec(
                repo.getFullName(), req.branch(), TEMPLATE_VERSION, appSlug, jobs);
        String yaml = renderer.render(spec);
        List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobs = renderer.expectedJobs(spec);
        String expectedJobsJson = writeExpectedJobsJson(expectedJobs);

        GitHubAutomationClient client = automationService.forInstallation(repo.getInstallationId());
        client.putFile(owner, name, WORKFLOW_PATH, yaml.getBytes(StandardCharsets.UTF_8),
                "Configure Deploy Platform managed workflow", defaultBranch);
        deploymentEventService.record(DeploymentEventType.REPO_CONNECTED, DeploymentEventStatus.SUCCESS,
                repo.getFullName(), null, null, "wrote workflow");

        client.dispatchWorkflow(owner, name, WORKFLOW_FILE, req.branch());
        deploymentEventService.record(DeploymentEventType.REPO_CONNECTED, DeploymentEventStatus.SUCCESS,
                repo.getFullName(), null, null, "dispatched");

        persist(repo, WorkflowMode.MANAGED, TEMPLATE_VERSION, expectedJobsJson);

        return new WiringResult(WorkflowMode.MANAGED, WORKFLOW_PATH, null);
    }

    private void persist(GitHubRepo repo, WorkflowMode mode, Integer templateVersion, String expectedJobsJson) {
        repo.setWorkflowMode(mode);
        repo.setWorkflowTemplateVersion(templateVersion);
        repo.setExpectedJobs(expectedJobsJson);
        repoRepository.save(repo);
    }

    private boolean isOurs(String content, String appSlug) {
        if (!content.contains("vector:managed")) {
            return false;
        }
        if (appSlug == null) {
            return false;
        }
        return content.contains("app=" + appSlug);
    }

    private String writeExpectedJobsJson(List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobs) {
        try {
            return MAPPER.writeValueAsString(expectedJobs);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize expected jobs", e);
        }
    }

    private String readExistingWorkflow(String owner, String name, String ref, long installationId) {
        String url = BASE_URL + "/repos/" + owner + "/" + name + "/contents/" + WORKFLOW_PATH + "?ref=" + ref;
        String token = configService.authProvider().tokenSupplier(installationId).get();
        String json = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + token)
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
            throw new GitHubException("Failed to parse contents response for: " + url, e);
        }
    }
}
