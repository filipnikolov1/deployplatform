package dev.filipnikolov.vector.connect.service;

import dev.filipnikolov.vector.ai.AiProvider;
import dev.filipnikolov.vector.connect.detect.BuildMode;
import dev.filipnikolov.vector.connect.dto.ConnectRequest;
import dev.filipnikolov.vector.connect.dto.ConnectResponse;
import dev.filipnikolov.vector.connect.workflow.ModuleJob;
import dev.filipnikolov.vector.connect.workflow.WiringResult;
import dev.filipnikolov.vector.connect.workflow.WorkflowWiringService;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.envvar.service.EnvVarService;
import dev.filipnikolov.vector.events.DeploySource;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.DeploymentStatus;
import dev.filipnikolov.vector.githubapp.model.GitHubInstallation;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.model.InstallationStatus;
import dev.filipnikolov.vector.githubapp.model.WorkflowMode;
import dev.filipnikolov.vector.githubapp.repository.GitHubInstallationRepository;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import dev.filipnikolov.vector.project.model.Project;
import dev.filipnikolov.vector.project.repository.ProjectEnvVarRepository;
import dev.filipnikolov.vector.project.repository.ProjectRepository;
import dev.filipnikolov.vector.project.repository.ProjectServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectServiceTest {

    private GitHubRepoRepository repoRepository;
    private GitHubInstallationRepository installationRepository;
    private GitHubAppConfigService configService;
    private ProjectRepository projectRepository;
    private ProjectServiceRepository projectServiceRepository;
    private ProjectEnvVarRepository projectEnvVarRepository;
    private DeploymentRepository deploymentRepository;
    private EnvVarService envVarService;
    private DeploymentEventService deploymentEventService;
    private WorkflowWiringService workflowWiringService;
    private AiProvider aiProvider;
    private dev.filipnikolov.vector.githubapp.service.GitHubAutomationService automationService;
    private ConnectService service;

    @BeforeEach
    void setUp() {
        repoRepository = mock(GitHubRepoRepository.class);
        installationRepository = mock(GitHubInstallationRepository.class);
        configService = mock(GitHubAppConfigService.class);
        projectRepository = mock(ProjectRepository.class);
        projectServiceRepository = mock(ProjectServiceRepository.class);
        projectEnvVarRepository = mock(ProjectEnvVarRepository.class);
        deploymentRepository = mock(DeploymentRepository.class);
        envVarService = mock(EnvVarService.class);
        deploymentEventService = mock(DeploymentEventService.class);
        workflowWiringService = mock(WorkflowWiringService.class);
        aiProvider = mock(AiProvider.class);
        RestClient.Builder restClientBuilder = RestClient.builder();
        automationService = mock(dev.filipnikolov.vector.githubapp.service.GitHubAutomationService.class);

        service = new ConnectService(repoRepository, installationRepository, configService,
                projectRepository, projectServiceRepository, projectEnvVarRepository,
                deploymentRepository, envVarService, deploymentEventService, workflowWiringService,
                aiProvider, restClientBuilder, automationService);

        when(workflowWiringService.wire(any(), any(), any()))
                .thenReturn(new WiringResult(WorkflowMode.MANAGED, ".github/workflows/vector-deploy.yml", null));
        when(projectRepository.save(any())).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(deploymentRepository.findByAppName(any())).thenReturn(Optional.empty());
        when(projectServiceRepository.findByAppName(any())).thenReturn(Optional.empty());
    }

    private GitHubRepo repo(String fullName, long installationId) {
        GitHubRepo repo = new GitHubRepo();
        repo.setFullName(fullName);
        repo.setInstallationId(installationId);
        repo.setDefaultBranch("main");
        return repo;
    }

    private GitHubInstallation installation(long id, InstallationStatus status) {
        GitHubInstallation installation = new GitHubInstallation();
        installation.setInstallationId(id);
        installation.setAccountLogin("alice");
        installation.setStatus(status);
        return installation;
    }

    private ConnectRequest.ModuleSelection module(String name, String path, boolean exposed) {
        return new ConnectRequest.ModuleSelection(name, path, "nextjs", BuildMode.BUILDPACK, 3000, null, exposed,
                Map.of("KEY", "val"));
    }

    @Test
    void connect_singleModule_registersAppOnlyWithNoProjectRow() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L)));
        when(installationRepository.findByInstallationId(1L)).thenReturn(Optional.of(installation(1L, InstallationStatus.APPROVED)));

        ConnectRequest req = new ConnectRequest("alice/shop", "main", List.of(module("shop-web", "apps/web", true)),
                WorkflowMode.MANAGED, false);

        ConnectResponse response = service.connect(req);

        assertThat(response.projectId()).isNull();
        assertThat(response.apps()).containsExactly("shop-web");
        verify(projectRepository, never()).save(any());

        ArgumentCaptor<Deployment> deploymentCaptor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository).save(deploymentCaptor.capture());
        Deployment saved = deploymentCaptor.getValue();
        assertThat(saved.getAppName()).isEqualTo("shop-web");
        assertThat(saved.getStatus()).isEqualTo(DeploymentStatus.PROVISIONING);
        assertThat(saved.getDeploySource()).isEqualTo(DeploySource.CONNECTED_REPO);
        assertThat(saved.getRepoUrl()).contains("alice/shop");
        assertThat(saved.getBranch()).isEqualTo("main");
    }

    @Test
    void connect_multiModule_createsProjectAndServicesAndApps() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L)));
        when(installationRepository.findByInstallationId(1L)).thenReturn(Optional.of(installation(1L, InstallationStatus.APPROVED)));

        ConnectRequest req = new ConnectRequest("alice/shop", "main",
                List.of(module("shop-web", "apps/web", true), module("shop-worker", "apps/worker", false)),
                WorkflowMode.MANAGED, false);

        ConnectResponse response = service.connect(req);

        assertThat(response.projectId()).isEqualTo(100L);
        assertThat(response.apps()).containsExactlyInAnyOrder("shop-web", "shop-worker");
        verify(projectRepository).save(any());
        verify(projectServiceRepository, times(2)).save(any());
        verify(deploymentRepository, times(2)).save(any());
    }

    @Test
    void connect_appNameCollisionWithDeployment_throwsAppNameTaken() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L)));
        when(installationRepository.findByInstallationId(1L)).thenReturn(Optional.of(installation(1L, InstallationStatus.APPROVED)));
        when(deploymentRepository.findByAppName("shop-web")).thenReturn(Optional.of(new Deployment()));

        ConnectRequest req = new ConnectRequest("alice/shop", "main", List.of(module("shop-web", "apps/web", true)),
                WorkflowMode.MANAGED, false);

        assertThatThrownBy(() -> service.connect(req)).isInstanceOf(AppNameTakenException.class);
        verify(deploymentRepository, never()).save(any());
    }

    @Test
    void connect_installationNotApproved_throwsInstallationNotApproved() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L)));
        when(installationRepository.findByInstallationId(1L)).thenReturn(Optional.of(installation(1L, InstallationStatus.PENDING)));

        ConnectRequest req = new ConnectRequest("alice/shop", "main", List.of(module("shop-web", "apps/web", true)),
                WorkflowMode.MANAGED, false);

        assertThatThrownBy(() -> service.connect(req)).isInstanceOf(InstallationNotApprovedException.class);
        verify(deploymentRepository, never()).save(any());
    }

    @Test
    void connect_recordsProvisioningStatusAndEvents() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L)));
        when(installationRepository.findByInstallationId(1L)).thenReturn(Optional.of(installation(1L, InstallationStatus.APPROVED)));

        ConnectRequest req = new ConnectRequest("alice/shop", "main", List.of(module("shop-web", "apps/web", true)),
                WorkflowMode.MANAGED, false);

        service.connect(req);

        verify(deploymentEventService).record(eq(DeploymentEventType.REPO_CONNECTED), eq(DeploymentEventStatus.SUCCESS),
                eq("alice/shop"), any(), any(), any());
        verify(deploymentEventService).record(eq(DeploymentEventType.REPO_CONNECTED), eq(DeploymentEventStatus.SUCCESS),
                eq("shop-web"), any(), any(), any());
    }

    @Test
    void connect_invokesWiringServiceWithConfirmedModuleList() {
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo("alice/shop", 1L)));
        when(installationRepository.findByInstallationId(1L)).thenReturn(Optional.of(installation(1L, InstallationStatus.APPROVED)));

        ConnectRequest req = new ConnectRequest("alice/shop", "main",
                List.of(module("shop-web", "apps/web", true), module("shop-worker", "apps/worker", false)),
                WorkflowMode.MANAGED, false);

        service.connect(req);

        ArgumentCaptor<List<ModuleJob>> jobsCaptor = ArgumentCaptor.forClass(List.class);
        verify(workflowWiringService).wire(any(), eq(req), jobsCaptor.capture());
        List<ModuleJob> jobs = jobsCaptor.getValue();
        assertThat(jobs).extracting(ModuleJob::appName).containsExactlyInAnyOrder("shop-web", "shop-worker");
        assertThat(jobs).extracting(ModuleJob::imageTarget)
                .containsExactlyInAnyOrder("ghcr.io/alice/shop-web", "ghcr.io/alice/shop-worker");
    }

    @Test
    void disconnect_clearsRepoUrlAndRecordsAudit() {
        Deployment deployment = new Deployment();
        deployment.setAppName("shop-web");
        deployment.setRepoUrl("https://github.com/alice/shop");
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("shop-web")).thenReturn(Optional.of(deployment));

        service.disconnect("shop-web");

        assertThat(deployment.getRepoUrl()).isNull();
        verify(deploymentRepository).save(deployment);
        verify(deploymentEventService).record(eq(DeploymentEventType.REPO_CONNECTED), eq(DeploymentEventStatus.SUCCESS),
                eq("shop-web"), any(), any(), any());
    }

    @Test
    void disconnect_appNotFound_throwsAppNotFoundException() {
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disconnect("missing")).isInstanceOf(AppNotFoundException.class);
    }

    @Test
    void redeploy_managedLane_dispatchesWorkflow() {
        Deployment deployment = new Deployment();
        deployment.setAppName("shop-web");
        deployment.setRepoUrl("https://github.com/alice/shop");
        deployment.setBranch("main");
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("shop-web")).thenReturn(Optional.of(deployment));

        GitHubRepo repo = repo("alice/shop", 1L);
        repo.setWorkflowMode(WorkflowMode.MANAGED);
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo));

        dev.filipnikolov.vector.github.client.GitHubAutomationClient client =
                mock(dev.filipnikolov.vector.github.client.GitHubAutomationClient.class);
        when(automationService.forInstallation(1L)).thenReturn(client);

        service.redeploy("shop-web");

        verify(client).dispatchWorkflow("alice", "shop", "vector-deploy.yml", "main");
    }

    @Test
    void redeploy_customLane_throwsCustomWorkflowRedeployException() {
        Deployment deployment = new Deployment();
        deployment.setAppName("shop-web");
        deployment.setRepoUrl("https://github.com/alice/shop");
        deployment.setBranch("main");
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("shop-web")).thenReturn(Optional.of(deployment));

        GitHubRepo repo = repo("alice/shop", 1L);
        repo.setWorkflowMode(WorkflowMode.CUSTOM);
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo));

        assertThatThrownBy(() -> service.redeploy("shop-web")).isInstanceOf(CustomWorkflowRedeployException.class);
    }

    @Test
    void ciStatus_returnsRecentRunsFromAutomationClient() {
        Deployment deployment = new Deployment();
        deployment.setAppName("shop-web");
        deployment.setRepoUrl("https://github.com/alice/shop");
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("shop-web")).thenReturn(Optional.of(deployment));

        GitHubRepo repo = repo("alice/shop", 1L);
        when(repoRepository.findByFullName("alice/shop")).thenReturn(Optional.of(repo));

        dev.filipnikolov.vector.github.client.GitHubAutomationClient client =
                mock(dev.filipnikolov.vector.github.client.GitHubAutomationClient.class);
        when(automationService.forInstallation(1L)).thenReturn(client);
        List<dev.filipnikolov.vector.github.client.dto.WorkflowRun> runs = List.of(
                new dev.filipnikolov.vector.github.client.dto.WorkflowRun(1L, "completed", "success", "https://x"));
        when(client.recentRuns("alice", "shop", "vector-deploy.yml")).thenReturn(runs);

        assertThat(service.ciStatus("shop-web")).isEqualTo(runs);
    }
}
