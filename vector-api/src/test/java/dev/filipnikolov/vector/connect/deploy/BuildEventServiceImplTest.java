package dev.filipnikolov.vector.connect.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.common.lock.ActionLockService;
import dev.filipnikolov.vector.connect.workflow.BuildpackWorkflowRenderer;
import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import dev.filipnikolov.vector.docker.service.DockerService;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.DeploymentStatus;
import dev.filipnikolov.vector.github.client.GitHubAutomationClient;
import dev.filipnikolov.vector.github.client.dto.RunJob;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.model.WorkflowMode;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAutomationService;
import dev.filipnikolov.vector.progress.ProgressFrame;
import dev.filipnikolov.vector.progress.ProgressHub;
import dev.filipnikolov.vector.project.model.Project;
import dev.filipnikolov.vector.project.model.ProjectService;
import dev.filipnikolov.vector.project.repository.ProjectRepository;
import dev.filipnikolov.vector.project.repository.ProjectServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildEventServiceImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GitHubRepoRepository repoRepository;
    private DeploymentRepository deploymentRepository;
    private ProjectRepository projectRepository;
    private ProjectServiceRepository projectServiceRepository;
    private PendingBuildRepository pendingBuildRepository;
    private DeploymentEventService deploymentEventService;
    private DeploymentService deploymentService;
    private DockerService dockerService;
    private ProgressHub progressHub;
    private GitHubAutomationService automationService;
    private GitHubAutomationClient automationClient;
    private BuildProgressMapper buildProgressMapper;
    private Executor deployExecutor;
    private ActionLockService actionLockService;
    private BuildEventServiceImpl service;

    @BeforeEach
    void setUp() {
        repoRepository = mock(GitHubRepoRepository.class);
        deploymentRepository = mock(DeploymentRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectServiceRepository = mock(ProjectServiceRepository.class);
        pendingBuildRepository = mock(PendingBuildRepository.class);
        deploymentEventService = mock(DeploymentEventService.class);
        deploymentService = mock(DeploymentService.class);
        dockerService = mock(DockerService.class);
        progressHub = mock(ProgressHub.class);
        automationService = mock(GitHubAutomationService.class);
        automationClient = mock(GitHubAutomationClient.class);
        buildProgressMapper = mock(BuildProgressMapper.class);
        deployExecutor = Runnable::run;
        actionLockService = new ActionLockService();

        when(automationService.forInstallation(anyLong())).thenReturn(automationClient);
        when(pendingBuildRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new BuildEventServiceImpl(repoRepository, deploymentRepository, projectRepository,
                projectServiceRepository, pendingBuildRepository, deploymentEventService, deploymentService,
                dockerService, progressHub, automationService, buildProgressMapper,
                deployExecutor, actionLockService);
    }

    private GitHubRepo repo(String fullName, long installationId, WorkflowMode mode) {
        GitHubRepo repo = new GitHubRepo();
        repo.setFullName(fullName);
        repo.setInstallationId(installationId);
        repo.setDefaultBranch("main");
        repo.setWorkflowMode(mode);
        return repo;
    }

    private JsonNode pushPayload(String repoFullName, String ref, String sha, String message, String author) {
        try {
            return MAPPER.readTree("""
                    {
                      "ref": "%s",
                      "after": "%s",
                      "repository": { "full_name": "%s" },
                      "head_commit": { "message": "%s", "author": { "name": "%s" } }
                    }
                    """.formatted(ref, sha, repoFullName, message, author));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handlePush_singleModuleApp_primesPendingBuildAndEmitsQueuedFrame() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L, WorkflowMode.MANAGED)));
        Deployment deployment = new Deployment();
        deployment.setAppName("shop");
        deployment.setBranch("main");
        deployment.setDeletedAt(null);
        when(deploymentRepository.findByRepoUrlAndDeletedAtIsNull("https://github.com/alice/shop"))
                .thenReturn(Optional.of(deployment));
        when(projectRepository.findByRepoFullName("alice/shop")).thenReturn(Optional.empty());
        when(pendingBuildRepository.findById("shop")).thenReturn(Optional.empty());

        JsonNode payload = pushPayload("alice/shop", "refs/heads/main", "abc123", "fix bug", "Alice");

        service.handlePush(payload);

        ArgumentCaptor<PendingBuild> captor = ArgumentCaptor.forClass(PendingBuild.class);
        verify(pendingBuildRepository).save(captor.capture());
        PendingBuild saved = captor.getValue();
        assertThat(saved.getAppName()).isEqualTo("shop");
        assertThat(saved.getHeadSha()).isEqualTo("abc123");
        assertThat(saved.getImageTarget()).contains("abc123");
        assertThat(saved.getStatus()).isEqualTo(PendingBuildStatus.QUEUED);

        verify(progressHub).start(any());
        verify(deploymentEventService).record(eq(DeploymentEventType.DEPLOY_TRIGGERED),
                eq(DeploymentEventStatus.IN_PROGRESS), eq("shop"), any(), any(), any(), any());
    }

    @Test
    void handlePush_nonDeployBranch_ignored() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L, WorkflowMode.MANAGED)));
        Deployment deployment = new Deployment();
        deployment.setAppName("shop");
        deployment.setBranch("main");
        when(deploymentRepository.findByRepoUrlAndDeletedAtIsNull("https://github.com/alice/shop"))
                .thenReturn(Optional.of(deployment));
        when(projectRepository.findByRepoFullName("alice/shop")).thenReturn(Optional.empty());

        JsonNode payload = pushPayload("alice/shop", "refs/heads/feature-x", "abc123", "wip", "Alice");

        service.handlePush(payload);

        verify(pendingBuildRepository, never()).save(any());
    }

    @Test
    void handlePush_unknownRepo_ignored() {
        when(repoRepository.findByFullName("alice/unknown")).thenReturn(Optional.empty());

        JsonNode payload = pushPayload("alice/unknown", "refs/heads/main", "abc123", "msg", "Alice");

        service.handlePush(payload);

        verify(pendingBuildRepository, never()).save(any());
    }

    @Test
    void handlePush_newerPush_overwritesOlderPendingBuildRow() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L, WorkflowMode.MANAGED)));
        Deployment deployment = new Deployment();
        deployment.setAppName("shop");
        deployment.setBranch("main");
        when(deploymentRepository.findByRepoUrlAndDeletedAtIsNull("https://github.com/alice/shop"))
                .thenReturn(Optional.of(deployment));
        when(projectRepository.findByRepoFullName("alice/shop")).thenReturn(Optional.empty());

        PendingBuild existing = new PendingBuild();
        existing.setAppName("shop");
        existing.setHeadSha("old-sha");
        existing.setOperationId("op-old");
        existing.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("shop")).thenReturn(Optional.of(existing));

        JsonNode payload = pushPayload("alice/shop", "refs/heads/main", "new-sha", "fix", "Alice");

        service.handlePush(payload);

        ArgumentCaptor<PendingBuild> captor = ArgumentCaptor.forClass(PendingBuild.class);
        verify(pendingBuildRepository).save(captor.capture());
        assertThat(captor.getValue().getHeadSha()).isEqualTo("new-sha");
        assertThat(captor.getValue().getAppName()).isEqualTo("shop");
    }

    private JsonNode workflowRunPayload(String action, String repoFullName, String headSha, long runId, String htmlUrl) {
        try {
            return MAPPER.readTree("""
                    {
                      "action": "%s",
                      "repository": { "full_name": "%s" },
                      "workflow_run": {
                        "id": %d,
                        "head_sha": "%s",
                        "path": ".github/workflows/vector-deploy.yml",
                        "html_url": "%s",
                        "run_started_at": "2026-07-08T10:00:00Z"
                      }
                    }
                    """.formatted(action, repoFullName, runId, headSha, htmlUrl));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handleWorkflowRun_requested_movesPendingBuildToBuildingWithRunMetadata() {
        when(deploymentRepository.findByRepoUrlAndDeletedAtIsNull("https://github.com/alice/shop"))
                .thenReturn(Optional.empty());
        when(projectRepository.findByRepoFullName("alice/shop")).thenReturn(Optional.of(project(1L, "alice/shop")));
        when(projectServiceRepository.findByProjectId(1L)).thenReturn(List.of(projectServiceRow("shop-web")));

        PendingBuild pending = new PendingBuild();
        pending.setAppName("shop-web");
        pending.setOperationId("op-1");
        pending.setHeadSha("abc123");
        pending.setStatus(PendingBuildStatus.QUEUED);
        when(pendingBuildRepository.findById("shop-web")).thenReturn(Optional.of(pending));

        service.handleWorkflowRun(workflowRunPayload("requested", "alice/shop", "abc123", 55L,
                "https://github.com/alice/shop/actions/runs/55"));

        ArgumentCaptor<PendingBuild> captor = ArgumentCaptor.forClass(PendingBuild.class);
        verify(pendingBuildRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PendingBuildStatus.BUILDING);
        assertThat(captor.getValue().getRunId()).isEqualTo(55L);
        assertThat(captor.getValue().getRunHtmlUrl()).isEqualTo("https://github.com/alice/shop/actions/runs/55");
    }

    @Test
    void handleWorkflowRun_wrongWorkflowPath_ignored() {
        JsonNode payload;
        try {
            payload = MAPPER.readTree("""
                    {
                      "action": "requested",
                      "repository": { "full_name": "alice/shop" },
                      "workflow_run": { "id": 1, "head_sha": "abc", "path": ".github/workflows/other.yml" }
                    }
                    """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        service.handleWorkflowRun(payload);

        verify(pendingBuildRepository, never()).findById(any());
    }

    private Project project(long id, String repoFullName) {
        Project p = new Project();
        p.setId(id);
        p.setRepoFullName(repoFullName);
        p.setDefaultBranch("main");
        return p;
    }

    private ProjectService projectServiceRow(String appName) {
        ProjectService s = new ProjectService();
        s.setAppName(appName);
        s.setProjectId(1L);
        return s;
    }

    @Test
    void handleWorkflowJob_completedSuccess_mergesStepsAndPrefetchesImage() {
        PendingBuild pending = new PendingBuild();
        pending.setAppName("shop-web");
        pending.setOperationId("op-1");
        pending.setHeadSha("abc123");
        pending.setImageTarget("ghcr.io/alice/shop-web:abc123");
        pending.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("shop-web")).thenReturn(Optional.of(pending));

        GitHubRepo repo = repo("alice/shop", 1L, WorkflowMode.MANAGED);
        repo.setExpectedJobs("[{\"jobName\":\"build-shop-web\",\"appName\":\"shop-web\",\"stepNames\":[\"Checkout\"]}]");
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo));

        JsonNode payload = workflowJobPayload("alice/shop", "build-shop-web", "completed", "success");
        when(buildProgressMapper.mapSteps(eq(payload), any())).thenReturn(
                List.of(new ProgressFrame("BUILD_STEP", "Checkout", null, null, null, Instant.now())));

        service.handleWorkflowJob(payload);

        verify(progressHub).emit(eq("op-1"), any());
        try {
            verify(dockerService).pullImage("ghcr.io/alice/shop-web:abc123");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handleWorkflowJob_completedSuccess_prefetchDispatchedAsyncNotOnCallingThread() throws Exception {
        PendingBuild pending = new PendingBuild();
        pending.setAppName("shop-web");
        pending.setOperationId("op-1");
        pending.setHeadSha("abc123");
        pending.setImageTarget("ghcr.io/alice/shop-web:abc123");
        pending.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("shop-web")).thenReturn(Optional.of(pending));

        GitHubRepo repo = repo("alice/shop", 1L, WorkflowMode.MANAGED);
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo));

        JsonNode payload = workflowJobPayload("alice/shop", "build-shop-web", "completed", "success");
        when(buildProgressMapper.mapSteps(eq(payload), any())).thenReturn(List.of());

        CountDownLatch taskSubmitted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        Executor blockingExecutor = task -> new Thread(() -> {
            taskSubmitted.countDown();
            try {
                releaseTask.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.run();
        }).start();

        BuildEventServiceImpl asyncService = new BuildEventServiceImpl(repoRepository, deploymentRepository,
                projectRepository, projectServiceRepository, pendingBuildRepository, deploymentEventService,
                deploymentService, dockerService, progressHub, automationService,
                buildProgressMapper, blockingExecutor, actionLockService);

        long start = System.currentTimeMillis();
        asyncService.handleWorkflowJob(payload);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(taskSubmitted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(elapsed).isLessThan(1000);
        verify(dockerService, never()).pullImage(any());

        releaseTask.countDown();
        Thread.sleep(200);
        verify(dockerService).pullImage("ghcr.io/alice/shop-web:abc123");
    }

    @Test
    void handleWorkflowJob_notCompleted_doesNotPrefetch() throws InterruptedException {
        PendingBuild pending = new PendingBuild();
        pending.setAppName("shop-web");
        pending.setOperationId("op-1");
        pending.setHeadSha("abc123");
        pending.setImageTarget("ghcr.io/alice/shop-web:abc123");
        pending.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("shop-web")).thenReturn(Optional.of(pending));

        GitHubRepo repo = repo("alice/shop", 1L, WorkflowMode.MANAGED);
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo));

        JsonNode payload = workflowJobPayload("alice/shop", "build-shop-web", "in_progress", null);
        when(buildProgressMapper.mapSteps(eq(payload), any())).thenReturn(List.of());

        service.handleWorkflowJob(payload);

        verify(dockerService, never()).pullImage(any());
    }

    @Test
    void handleWorkflowJob_staleHeadSha_ignoredEvent() throws InterruptedException {
        PendingBuild pending = new PendingBuild();
        pending.setAppName("shop-web");
        pending.setOperationId("op-2");
        pending.setHeadSha("new-sha");
        pending.setImageTarget("ghcr.io/alice/shop-web:new-sha");
        pending.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("shop-web")).thenReturn(Optional.of(pending));

        GitHubRepo repo = repo("alice/shop", 1L, WorkflowMode.MANAGED);
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo));

        JsonNode payload = workflowJobPayload("alice/shop", "build-shop-web", "completed", "success", "old-sha");

        service.handleWorkflowJob(payload);

        verify(progressHub, never()).emit(any(), any());
        verify(dockerService, never()).pullImage(any());
    }

    private JsonNode workflowJobPayload(String repoFullName, String jobName, String status, String conclusion) {
        return workflowJobPayload(repoFullName, jobName, status, conclusion, "abc123");
    }

    private JsonNode workflowJobPayload(String repoFullName, String jobName, String status, String conclusion, String headSha) {
        try {
            String conclusionField = conclusion == null ? "null" : "\"" + conclusion + "\"";
            return MAPPER.readTree("""
                    {
                      "repository": { "full_name": "%s" },
                      "workflow_job": { "name": "%s", "status": "%s", "conclusion": %s, "head_sha": "%s", "steps": [] }
                    }
                    """.formatted(repoFullName, jobName, status, conclusionField, headSha));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handleWorkflowRun_completed_allJobsSucceed_swapsEachAppWithHeadShaImage() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L, WorkflowMode.MANAGED)));
        when(automationClient.listRunJobs("alice", "shop", 55L)).thenReturn(List.of(
                new RunJob(1L, "build-shop-web", "completed", "success", "https://github.com/x/job/1")));

        PendingBuild pending = new PendingBuild();
        pending.setAppName("shop-web");
        pending.setOperationId("op-1");
        pending.setHeadSha("abc123");
        pending.setImageTarget("ghcr.io/alice/shop-web:abc123");
        pending.setBranch("main");
        pending.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("shop-web")).thenReturn(Optional.of(pending));
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("shop-web")).thenReturn(Optional.empty());

        service.handleWorkflowRun(workflowRunPayload("completed", "alice/shop", "abc123", 55L,
                "https://github.com/alice/shop/actions/runs/55"));

        ArgumentCaptor<CreateDeploymentRequest> captor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
        verify(deploymentService).handleWebhookDeployAsync(captor.capture(), any());
        assertThat(captor.getValue().appName()).isEqualTo("shop-web");
        assertThat(captor.getValue().imageName()).isEqualTo("ghcr.io/alice/shop-web:abc123");
    }

    @Test
    void handleWorkflowRun_completed_oneJobFailed_swapsNothingAndRecordsBuildFailed() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L, WorkflowMode.MANAGED)));
        when(automationClient.listRunJobs("alice", "shop", 55L)).thenReturn(List.of(
                new RunJob(1L, "build-shop-web", "completed", "success", "https://github.com/x/job/1"),
                new RunJob(2L, "build-shop-api", "completed", "failure", "https://github.com/x/job/2")));

        when(deploymentRepository.findByRepoUrlAndDeletedAtIsNull("https://github.com/alice/shop"))
                .thenReturn(Optional.empty());
        when(projectRepository.findByRepoFullName("alice/shop")).thenReturn(Optional.empty());

        service.handleWorkflowRun(workflowRunPayload("completed", "alice/shop", "abc123", 55L,
                "https://github.com/alice/shop/actions/runs/55"));

        verify(deploymentService, never()).handleWebhookDeployAsync(any(), any());
        verify(deploymentEventService).record(eq(DeploymentEventType.BUILD_FAILED), eq(DeploymentEventStatus.FAILURE),
                eq("shop-api"), any(), any(), any());
    }

    @Test
    void handleWorkflowRun_completed_twoJobsFailed_recordsBuildFailedForEachAndSwapsNothing() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L, WorkflowMode.MANAGED)));
        when(automationClient.listRunJobs("alice", "shop", 55L)).thenReturn(List.of(
                new RunJob(1L, "build-shop-web", "completed", "failure", "https://github.com/x/job/1"),
                new RunJob(2L, "build-shop-api", "completed", "failure", "https://github.com/x/job/2")));

        when(deploymentRepository.findByRepoUrlAndDeletedAtIsNull("https://github.com/alice/shop"))
                .thenReturn(Optional.empty());
        when(projectRepository.findByRepoFullName("alice/shop")).thenReturn(Optional.empty());

        service.handleWorkflowRun(workflowRunPayload("completed", "alice/shop", "abc123", 55L,
                "https://github.com/alice/shop/actions/runs/55"));

        verify(deploymentService, never()).handleWebhookDeployAsync(any(), any());
        verify(deploymentEventService).record(eq(DeploymentEventType.BUILD_FAILED), eq(DeploymentEventStatus.FAILURE),
                eq("shop-web"), any(), any(), any());
        verify(deploymentEventService).record(eq(DeploymentEventType.BUILD_FAILED), eq(DeploymentEventStatus.FAILURE),
                eq("shop-api"), any(), any(), any());
    }

    @Test
    void handleWorkflowRun_completed_skippedJob_appUntouched() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L, WorkflowMode.MANAGED)));
        when(automationClient.listRunJobs("alice", "shop", 55L)).thenReturn(List.of(
                new RunJob(1L, "build-shop-web", "completed", "skipped", "https://github.com/x/job/1")));

        service.handleWorkflowRun(workflowRunPayload("completed", "alice/shop", "abc123", 55L,
                "https://github.com/alice/shop/actions/runs/55"));

        verify(deploymentService, never()).handleWebhookDeployAsync(any(), any());
    }

    @Test
    void handleWorkflowRun_completed_staleShaOlderThanPending_skipsSwap() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L, WorkflowMode.MANAGED)));
        when(automationClient.listRunJobs("alice", "shop", 55L)).thenReturn(List.of(
                new RunJob(1L, "build-shop-web", "completed", "success", "https://github.com/x/job/1")));

        PendingBuild pending = new PendingBuild();
        pending.setAppName("shop-web");
        pending.setOperationId("op-2");
        pending.setHeadSha("newer-sha");
        pending.setImageTarget("ghcr.io/alice/shop-web:newer-sha");
        pending.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("shop-web")).thenReturn(Optional.of(pending));

        service.handleWorkflowRun(workflowRunPayload("completed", "alice/shop", "abc123", 55L,
                "https://github.com/alice/shop/actions/runs/55"));

        verify(deploymentService, never()).handleWebhookDeployAsync(any(), any());
    }

    @Test
    void handleWorkflowRun_completed_selfApp_routesThroughHookGatedSwap() {
        when(repoRepository.findByFullName("filipnikolov1/vector-platform")).thenReturn(
                Optional.of(repo("filipnikolov1/vector-platform", 1L, WorkflowMode.CUSTOM)));
        when(automationClient.listRunJobs("filipnikolov1", "vector-platform", 55L)).thenReturn(List.of(
                new RunJob(1L, "build-vector-api", "completed", "success", "https://github.com/x/job/1")));

        PendingBuild pending = new PendingBuild();
        pending.setAppName("vector-api");
        pending.setOperationId("op-1");
        pending.setHeadSha("abc123");
        pending.setImageTarget("ghcr.io/filipnikolov1/vector-api:abc123");
        pending.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("vector-api")).thenReturn(Optional.of(pending));

        service.handleWorkflowRun(workflowRunPayload("completed", "filipnikolov1/vector-platform", "abc123", 55L,
                "https://github.com/x/actions/runs/55"));

        ArgumentCaptor<CreateDeploymentRequest> captor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
        verify(deploymentService).handleWebhookDeployAsync(captor.capture(), any());
        assertThat(captor.getValue().appName()).isEqualTo("vector-api");
        assertThat(captor.getValue().imageName()).isEqualTo("ghcr.io/filipnikolov1/vector-api:abc123");
    }

    @Test
    void handleWorkflowRun_completed_lockHeldForApp_skipsThatAppWithoutFailingOthers() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L, WorkflowMode.MANAGED)));
        when(automationClient.listRunJobs("alice", "shop", 55L)).thenReturn(List.of(
                new RunJob(1L, "build-shop-web", "completed", "success", "https://github.com/x/job/1"),
                new RunJob(2L, "build-shop-api", "completed", "success", "https://github.com/x/job/2")));

        PendingBuild pendingWeb = new PendingBuild();
        pendingWeb.setAppName("shop-web");
        pendingWeb.setOperationId("op-1");
        pendingWeb.setHeadSha("abc123");
        pendingWeb.setImageTarget("ghcr.io/alice/shop-web:abc123");
        pendingWeb.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("shop-web")).thenReturn(Optional.of(pendingWeb));

        PendingBuild pendingApi = new PendingBuild();
        pendingApi.setAppName("shop-api");
        pendingApi.setOperationId("op-2");
        pendingApi.setHeadSha("abc123");
        pendingApi.setImageTarget("ghcr.io/alice/shop-api:abc123");
        pendingApi.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("shop-api")).thenReturn(Optional.of(pendingApi));

        when(deploymentRepository.findByAppNameAndDeletedAtIsNull(any())).thenReturn(Optional.empty());

        ActionLockService.LockHandle heldLock = actionLockService.tryLock("app:shop-web").orElseThrow();
        try {
            service.handleWorkflowRun(workflowRunPayload("completed", "alice/shop", "abc123", 55L,
                    "https://github.com/alice/shop/actions/runs/55"));

            ArgumentCaptor<CreateDeploymentRequest> captor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
            verify(deploymentService).handleWebhookDeployAsync(captor.capture(), any());
            assertThat(captor.getValue().appName()).isEqualTo("shop-api");
            verify(deploymentEventService).record(eq(DeploymentEventType.WEBHOOK_IGNORED), any(),
                    eq("shop-web"), any(), any(), any());
        } finally {
            heldLock.close();
        }
    }

    @Test
    void handlePush_managedWorkflowFileTampered_flipsToCustomAndRecordsAudit() {
        GitHubRepo repo = repo("alice/shop", 1L, WorkflowMode.MANAGED);
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo));
        when(deploymentRepository.findByRepoUrlAndDeletedAtIsNull("https://github.com/alice/shop"))
                .thenReturn(Optional.empty());
        when(projectRepository.findByRepoFullName("alice/shop")).thenReturn(Optional.empty());
        when(automationClient.readFile("alice", "shop", ".github/workflows/vector-deploy.yml", "main"))
                .thenReturn("some custom workflow without the marker");

        JsonNode payload;
        try {
            payload = MAPPER.readTree("""
                    {
                      "ref": "refs/heads/main",
                      "after": "abc123",
                      "repository": { "full_name": "alice/shop" },
                      "head_commit": { "message": "edit workflow", "author": { "name": "Alice" } },
                      "commits": [ { "modified": [".github/workflows/vector-deploy.yml"], "added": [] } ]
                    }
                    """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        service.handlePush(payload);

        assertThat(repo.getWorkflowMode()).isEqualTo(WorkflowMode.CUSTOM);
        verify(repoRepository).save(repo);
        verify(deploymentEventService).record(eq(DeploymentEventType.REPO_CONNECTED),
                eq(DeploymentEventStatus.SUCCESS), eq("alice/shop"), any(), any(), any());
    }

    @Test
    void handlePush_managedWorkflowFileEditedButMarkerIntact_stayManaged() {
        GitHubRepo repo = repo("alice/shop", 1L, WorkflowMode.MANAGED);
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo));
        when(deploymentRepository.findByRepoUrlAndDeletedAtIsNull("https://github.com/alice/shop"))
                .thenReturn(Optional.empty());
        when(projectRepository.findByRepoFullName("alice/shop")).thenReturn(Optional.empty());
        when(automationClient.readFile("alice", "shop", ".github/workflows/vector-deploy.yml", "main"))
                .thenReturn("# vector:managed app=vector-bot\n...");

        JsonNode payload;
        try {
            payload = MAPPER.readTree("""
                    {
                      "ref": "refs/heads/main",
                      "after": "abc123",
                      "repository": { "full_name": "alice/shop" },
                      "head_commit": { "message": "noop", "author": { "name": "Alice" } },
                      "commits": [ { "modified": [".github/workflows/vector-deploy.yml"], "added": [] } ]
                    }
                    """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        service.handlePush(payload);

        assertThat(repo.getWorkflowMode()).isEqualTo(WorkflowMode.MANAGED);
        verify(repoRepository, never()).save(any());
    }

    @Test
    void handleWorkflowJob_prefetchThrows_doesNotPropagate() {
        PendingBuild pending = new PendingBuild();
        pending.setAppName("shop-web");
        pending.setOperationId("op-1");
        pending.setHeadSha("abc123");
        pending.setImageTarget("ghcr.io/alice/shop-web:abc123");
        pending.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("shop-web")).thenReturn(Optional.of(pending));

        GitHubRepo repo = repo("alice/shop", 1L, WorkflowMode.MANAGED);
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo));

        JsonNode payload = workflowJobPayload("alice/shop", "build-shop-web", "completed", "success");
        when(buildProgressMapper.mapSteps(eq(payload), any())).thenReturn(List.of());
        try {
            org.mockito.Mockito.doThrow(new RuntimeException("pull failed"))
                    .when(dockerService).pullImage("ghcr.io/alice/shop-web:abc123");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.handleWorkflowJob(payload));
    }

    @Test
    void handleWorkflowRun_completed_prefetchNeverHappened_swapStillSucceedsViaFreshPull() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L, WorkflowMode.MANAGED)));
        when(automationClient.listRunJobs("alice", "shop", 55L)).thenReturn(List.of(
                new RunJob(1L, "build-shop-web", "completed", "success", "https://github.com/x/job/1")));

        PendingBuild pending = new PendingBuild();
        pending.setAppName("shop-web");
        pending.setOperationId("op-1");
        pending.setHeadSha("abc123");
        pending.setImageTarget("ghcr.io/alice/shop-web:abc123");
        pending.setBranch("main");
        pending.setStatus(PendingBuildStatus.BUILDING);
        when(pendingBuildRepository.findById("shop-web")).thenReturn(Optional.of(pending));
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("shop-web")).thenReturn(Optional.empty());

        service.handleWorkflowRun(workflowRunPayload("completed", "alice/shop", "abc123", 55L,
                "https://github.com/alice/shop/actions/runs/55"));

        verify(deploymentService).handleWebhookDeployAsync(any(), any());
    }
}
