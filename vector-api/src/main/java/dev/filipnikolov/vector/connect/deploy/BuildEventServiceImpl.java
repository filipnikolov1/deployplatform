package dev.filipnikolov.vector.connect.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.common.lock.ActionLockService;
import dev.filipnikolov.vector.connect.workflow.BuildpackWorkflowRenderer;
import dev.filipnikolov.vector.connect.workflow.ModuleJob;
import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import dev.filipnikolov.vector.docker.service.DockerService;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.TriggerSource;
import dev.filipnikolov.vector.github.client.GitHubAutomationClient;
import dev.filipnikolov.vector.github.client.dto.RunJob;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.model.WorkflowMode;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAutomationService;
import dev.filipnikolov.vector.progress.ProgressFrame;
import dev.filipnikolov.vector.progress.ProgressHub;
import dev.filipnikolov.vector.project.model.Project;
import dev.filipnikolov.vector.project.repository.ProjectRepository;
import dev.filipnikolov.vector.project.repository.ProjectServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class BuildEventServiceImpl implements BuildEventService {

    private static final Logger log = LoggerFactory.getLogger(BuildEventServiceImpl.class);
    private static final String WORKFLOW_PATH = ".github/workflows/vector-deploy.yml";
    private static final ObjectMapper MODULE_JOBS_MAPPER = new ObjectMapper();

    private final GitHubRepoRepository repoRepository;
    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectServiceRepository projectServiceRepository;
    private final PendingBuildRepository pendingBuildRepository;
    private final DeploymentEventService deploymentEventService;
    private final DeploymentService deploymentService;
    private final DockerService dockerService;
    private final ProgressHub progressHub;
    private final GitHubAutomationService automationService;
    private final BuildProgressMapper buildProgressMapper;
    private final Executor deployExecutor;
    private final ActionLockService actionLockService;

    public BuildEventServiceImpl(GitHubRepoRepository repoRepository,
                                  DeploymentRepository deploymentRepository,
                                  ProjectRepository projectRepository,
                                  ProjectServiceRepository projectServiceRepository,
                                  PendingBuildRepository pendingBuildRepository,
                                  DeploymentEventService deploymentEventService,
                                  DeploymentService deploymentService,
                                  DockerService dockerService,
                                  ProgressHub progressHub,
                                  GitHubAutomationService automationService,
                                  BuildProgressMapper buildProgressMapper,
                                  @Qualifier("deployExecutor") Executor deployExecutor,
                                  ActionLockService actionLockService) {
        this.repoRepository = repoRepository;
        this.deploymentRepository = deploymentRepository;
        this.projectRepository = projectRepository;
        this.projectServiceRepository = projectServiceRepository;
        this.pendingBuildRepository = pendingBuildRepository;
        this.deploymentEventService = deploymentEventService;
        this.deploymentService = deploymentService;
        this.dockerService = dockerService;
        this.progressHub = progressHub;
        this.automationService = automationService;
        this.buildProgressMapper = buildProgressMapper;
        this.deployExecutor = deployExecutor;
        this.actionLockService = actionLockService;
    }

    private record AppTarget(String appName, String branch) {}

    @Override
    @Transactional
    public void handlePush(JsonNode payload) {
        String repoFullName = payload.path("repository").path("full_name").asText(null);
        if (repoFullName == null) {
            return;
        }
        GitHubRepo repo = repoRepository.findByFullName(repoFullName).orElse(null);
        if (repo == null) {
            return;
        }

        String ref = payload.path("ref").asText("");
        String branch = ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
        String headSha = payload.path("after").asText(null);
        String commitMessage = payload.path("head_commit").path("message").asText(null);
        String commitAuthor = payload.path("head_commit").path("author").path("name").asText(null);

        checkWorkflowTamper(repo, payload);

        List<ModuleJob> jobs = readModuleJobsSnapshot(repo);
        for (AppTarget target : resolveAppsForRepo(repoFullName)) {
            if (!branch.equals(target.branch())) {
                continue;
            }
            String imageTarget = imageTargetForApp(repo, jobs, target.appName()) + ":" + headSha;
            primePendingBuild(target.appName(), headSha, imageTarget, branch, commitMessage, commitAuthor);
        }
    }

    private void primePendingBuild(String appName, String headSha, String imageTarget, String branch,
                                    String commitMessage, String commitAuthor) {
        String operationId = UUID.randomUUID().toString();
        PendingBuild pending = new PendingBuild();
        pending.setAppName(appName);
        pending.setOperationId(operationId);
        pending.setHeadSha(headSha);
        pending.setImageTarget(imageTarget);
        pending.setBranch(branch);
        pending.setCommitMessage(commitMessage);
        pending.setCommitAuthor(commitAuthor);
        pending.setStatus(PendingBuildStatus.QUEUED);
        pending.setCreatedAt(LocalDateTime.now());
        pending.setUpdatedAt(LocalDateTime.now());
        pendingBuildRepository.save(pending);

        progressHub.start(operationId);
        progressHub.emit(operationId, new ProgressFrame("QUEUED", "Build queued", null, null, null, Instant.now()));

        CreateDeploymentRequest ctx = new CreateDeploymentRequest(
                appName, null, imageTarget, null, branch, headSha, commitMessage, commitAuthor,
                null, null, TriggerSource.AUTOMATIC, operationId);
        deploymentEventService.record(DeploymentEventType.DEPLOY_TRIGGERED, DeploymentEventStatus.IN_PROGRESS,
                appName, ctx, null, null, operationId);
    }

    private void checkWorkflowTamper(GitHubRepo repo, JsonNode payload) {
        if (repo.getWorkflowMode() != WorkflowMode.MANAGED) {
            return;
        }
        boolean touchesWorkflow = false;
        for (String field : List.of("added", "modified")) {
            for (JsonNode commit : payload.path("commits")) {
                for (JsonNode path : commit.path(field)) {
                    if (WORKFLOW_PATH.equals(path.asText())) {
                        touchesWorkflow = true;
                    }
                }
            }
        }
        if (!touchesWorkflow) {
            return;
        }
        String[] parts = repo.getFullName().split("/", 2);
        GitHubAutomationClient client = automationService.forInstallation(repo.getInstallationId());
        String branch = payload.path("ref").asText("").replaceFirst("^refs/heads/", "");
        String content;
        try {
            content = client.readFile(parts[0], parts[1], WORKFLOW_PATH, branch);
        } catch (Exception e) {
            log.warn("Failed to read {} for tamper check on {}: {}", WORKFLOW_PATH, repo.getFullName(), e.getMessage());
            return;
        }
        if (content == null || !content.contains("vector:managed")) {
            repo.setWorkflowMode(WorkflowMode.CUSTOM);
            repoRepository.save(repo);
            deploymentEventService.record(DeploymentEventType.REPO_CONNECTED, DeploymentEventStatus.SUCCESS,
                    repo.getFullName(), null, null, "workflow marker lost — switched to CUSTOM");
        }
    }

    @Override
    public void handleWorkflowRun(JsonNode payload) {
        JsonNode run = payload.path("workflow_run");
        String path = run.path("path").asText("");
        if (!WORKFLOW_PATH.equals(path)) {
            return;
        }
        String repoFullName = payload.path("repository").path("full_name").asText(null);
        String action = payload.path("action").asText("");
        String headSha = run.path("head_sha").asText(null);
        long runId = run.path("id").asLong();
        String htmlUrl = run.path("html_url").asText(null);
        String startedAt = run.path("run_started_at").asText(null);

        if ("requested".equals(action) || "in_progress".equals(action)) {
            for (AppTarget target : resolveAppsForRepo(repoFullName)) {
                pendingBuildRepository.findById(target.appName()).ifPresent(pending -> {
                    if (!pending.getHeadSha().equals(headSha)) {
                        return;
                    }
                    pending.setStatus(PendingBuildStatus.BUILDING);
                    pending.setRunId(runId);
                    pending.setRunHtmlUrl(htmlUrl);
                    pending.setUpdatedAt(LocalDateTime.now());
                    pendingBuildRepository.save(pending);
                    progressHub.emit(pending.getOperationId(), new ProgressFrame(
                            "BUILDING", "Build started" + (startedAt != null ? " at " + startedAt : ""),
                            null, null, null, Instant.now()));
                });
            }
            return;
        }

        if ("completed".equals(action)) {
            handleRunCompleted(repoFullName, runId, headSha, htmlUrl);
        }
    }

    private void handleRunCompleted(String repoFullName, long runId, String headSha, String runHtmlUrl) {
        GitHubRepo repo = repoRepository.findByFullName(repoFullName).orElse(null);
        if (repo == null) {
            return;
        }
        String[] parts = repo.getFullName().split("/", 2);
        GitHubAutomationClient client = automationService.forInstallation(repo.getInstallationId());
        List<RunJob> jobs;
        try {
            jobs = client.listRunJobs(parts[0], parts[1], runId);
        } catch (Exception e) {
            log.error("Failed to fetch jobs for run {} of {}: {}", runId, repoFullName, e.getMessage(), e);
            return;
        }

        List<RunJob> buildJobs = jobs.stream().filter(j -> j.name().startsWith("build-")).toList();
        List<RunJob> failedJobs = buildJobs.stream()
                .filter(j -> !"success".equals(j.conclusion()) && !"skipped".equals(j.conclusion()))
                .toList();

        if (!failedJobs.isEmpty()) {
            for (RunJob failedJob : failedJobs) {
                String appName = failedJob.name().substring("build-".length());
                deploymentEventService.record(DeploymentEventType.BUILD_FAILED, DeploymentEventStatus.FAILURE,
                        appName, null, null, "Job " + failedJob.name() + " failed — " + runHtmlUrl);
            }
            clearPendingBuildsForRun(repoFullName, headSha);
            return;
        }

        for (RunJob job : buildJobs) {
            if ("skipped".equals(job.conclusion())) {
                continue;
            }
            String appName = job.name().substring("build-".length());
            swapApp(appName, headSha);
        }
    }

    private void clearPendingBuildsForRun(String repoFullName, String headSha) {
        for (AppTarget target : resolveAppsForRepo(repoFullName)) {
            pendingBuildRepository.findById(target.appName()).ifPresent(pending -> {
                if (headSha.equals(pending.getHeadSha())) {
                    pendingBuildRepository.deleteById(target.appName());
                }
            });
        }
    }

    private void swapApp(String appName, String headSha) {
        PendingBuild pending = pendingBuildRepository.findById(appName).orElse(null);
        if (pending == null || !pending.getHeadSha().equals(headSha)) {
            return;
        }

        CreateDeploymentRequest req = new CreateDeploymentRequest(
                appName, null, pending.getImageTarget(), null, pending.getBranch(), pending.getHeadSha(),
                pending.getCommitMessage(), pending.getCommitAuthor(), null, null, TriggerSource.AUTOMATIC,
                pending.getOperationId());
        pendingBuildRepository.deleteById(appName);

        Optional<ActionLockService.LockHandle> maybeLock = actionLockService.tryLock("app:" + appName);
        if (maybeLock.isEmpty()) {
            log.warn("Deploy lock held for {}, skipping run-completed swap", appName);
            deploymentEventService.record(DeploymentEventType.WEBHOOK_IGNORED, DeploymentEventStatus.FAILURE,
                    appName, req, null, "Deployment already in progress for " + appName);
            return;
        }

        ActionLockService.LockHandle lock = maybeLock.get();
        try {
            deploymentService.handleWebhookDeployAsync(req, lock);
        } catch (TaskRejectedException e) {
            // Async submission failed synchronously (executor saturated / shutting down).
            // The @Async method never runs, so its finally-close never fires — release here
            // or the per-app semaphore is leaked permanently.
            lock.close();
            log.warn("Deploy executor rejected task for {}: {}", appName, e.getMessage());
        }
    }

    @Override
    public void handleWorkflowJob(JsonNode payload) {
        JsonNode job = payload.path("workflow_job");
        String jobName = job.path("name").asText(null);
        if (jobName == null || !jobName.startsWith("build-")) {
            return;
        }
        String appName = jobName.substring("build-".length());
        String headSha = job.path("head_sha").asText(null);
        PendingBuild pending = pendingBuildRepository.findById(appName).orElse(null);
        if (pending == null || !pending.getHeadSha().equals(headSha)) {
            return;
        }

        List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobs = expectedJobsForApp(payload, appName);
        for (ProgressFrame frame : buildProgressMapper.mapSteps(payload, expectedJobs)) {
            progressHub.emit(pending.getOperationId(), frame);
        }

        String status = job.path("status").asText("");
        String conclusion = job.hasNonNull("conclusion") ? job.path("conclusion").asText() : null;
        if ("completed".equals(status) && "success".equals(conclusion)) {
            String imageTarget = pending.getImageTarget();
            deployExecutor.execute(() -> {
                try {
                    dockerService.pullImage(imageTarget);
                } catch (Exception e) {
                    log.warn("Prefetch failed for {} ({}): {}", appName, imageTarget, e.getMessage());
                }
            });
        }
    }

    private List<BuildpackWorkflowRenderer.ExpectedJob> expectedJobsForApp(JsonNode payload, String appName) {
        String repoFullName = payload.path("repository").path("full_name").asText(null);
        if (repoFullName == null) {
            return List.of();
        }
        GitHubRepo repo = repoRepository.findByFullName(repoFullName).orElse(null);
        if (repo == null || repo.getExpectedJobs() == null) {
            return List.of();
        }
        try {
            return List.of(MODULE_JOBS_MAPPER.readValue(repo.getExpectedJobs(), BuildpackWorkflowRenderer.ExpectedJob[].class));
        } catch (Exception e) {
            log.warn("Failed to parse expected_jobs snapshot for {}: {}", repoFullName, e.getMessage());
            return List.of();
        }
    }

    private List<AppTarget> resolveAppsForRepo(String repoFullName) {
        String repoUrl = "https://github.com/" + repoFullName;
        Optional<Deployment> single = deploymentRepository.findByRepoUrlAndDeletedAtIsNull(repoUrl);
        if (single.isPresent()) {
            Deployment d = single.get();
            return List.of(new AppTarget(d.getAppName(), d.getBranch()));
        }

        Optional<Project> project = projectRepository.findByRepoFullName(repoFullName);
        if (project.isPresent()) {
            return projectServiceRepository.findByProjectId(project.get().getId()).stream()
                    .map(s -> new AppTarget(s.getAppName(), project.get().getDefaultBranch()))
                    .toList();
        }
        return List.of();
    }

    private String imageTargetForApp(GitHubRepo repo, List<ModuleJob> jobs, String appName) {
        return jobs.stream()
                .filter(j -> j.appName().equals(appName))
                .map(ModuleJob::imageTarget)
                .findFirst()
                .orElseGet(() -> {
                    String owner = repo.getFullName().split("/", 2)[0];
                    return "ghcr.io/" + owner.toLowerCase(Locale.ROOT) + "/" + appName.toLowerCase(Locale.ROOT);
                });
    }

    private List<ModuleJob> readModuleJobsSnapshot(GitHubRepo repo) {
        if (repo.getModuleJobs() == null) {
            return List.of();
        }
        try {
            return List.of(MODULE_JOBS_MAPPER.readValue(repo.getModuleJobs(), ModuleJob[].class));
        } catch (Exception e) {
            log.warn("Failed to parse module_jobs snapshot for {}: {}", repo.getFullName(), e.getMessage());
            return List.of();
        }
    }
}
