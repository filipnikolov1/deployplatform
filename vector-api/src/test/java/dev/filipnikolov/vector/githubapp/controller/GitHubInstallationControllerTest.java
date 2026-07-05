package dev.filipnikolov.vector.githubapp.controller;

import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.githubapp.model.GitHubInstallation;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.model.InstallationStatus;
import dev.filipnikolov.vector.githubapp.repository.GitHubInstallationRepository;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import dev.filipnikolov.vector.githubapp.service.InstallationSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubInstallationControllerTest {

    private MockMvc mockMvc;
    private GitHubInstallationRepository installationRepository;
    private GitHubRepoRepository repoRepository;
    private GitHubAppConfigService configService;
    private InstallationSyncService installationSyncService;
    private DeploymentEventService deploymentEventService;
    private DeploymentRepository deploymentRepository;

    @BeforeEach
    void setUp() {
        installationRepository = mock(GitHubInstallationRepository.class);
        repoRepository = mock(GitHubRepoRepository.class);
        configService = mock(GitHubAppConfigService.class);
        installationSyncService = mock(InstallationSyncService.class);
        deploymentEventService = mock(DeploymentEventService.class);
        deploymentRepository = mock(DeploymentRepository.class);

        when(configService.resolve()).thenReturn(Optional.of(
                new GitHubAppConfigService.AppCredentials("1", null, "secret", "filip")));

        GitHubInstallationController controller = new GitHubInstallationController(
                installationRepository, repoRepository, configService,
                installationSyncService, deploymentEventService, deploymentRepository);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private GitHubInstallation installation(long id, String login, InstallationStatus status, boolean suspended) {
        GitHubInstallation installation = new GitHubInstallation();
        installation.setInstallationId(id);
        installation.setAccountLogin(login);
        installation.setAccountType("User");
        installation.setStatus(status);
        installation.setSuspended(suspended);
        return installation;
    }

    @Test
    void listInstallations_returnsShapeWithRepoCount() throws Exception {
        GitHubInstallation installation = installation(42L, "filip", InstallationStatus.APPROVED, false);
        when(installationRepository.findAll()).thenReturn(List.of(installation));
        when(repoRepository.findByInstallationId(42L)).thenReturn(List.of(new GitHubRepo(), new GitHubRepo()));

        mockMvc.perform(get("/api/github/installations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].installationId").value(42))
                .andExpect(jsonPath("$[0].accountLogin").value("filip"))
                .andExpect(jsonPath("$[0].accountType").value("User"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].suspended").value(false))
                .andExpect(jsonPath("$[0].repoCount").value(2));
    }

    @Test
    void listInstallations_appUnconfigured_returns409() throws Exception {
        when(configService.resolve()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/github/installations"))
                .andExpect(status().isConflict());
    }

    @Test
    void approveInstallation_setsApprovedAndSyncsRepos() throws Exception {
        GitHubInstallation installation = installation(42L, "friend", InstallationStatus.PENDING, false);
        when(installationRepository.findByInstallationId(42L)).thenReturn(Optional.of(installation));
        when(installationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/github/installations/42/approve"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(GitHubInstallation.class);
        verify(installationRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getStatus()).isEqualTo(InstallationStatus.APPROVED);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getApprovedAt()).isNotNull();

        verify(deploymentEventService).record(
                eq(DeploymentEventType.INSTALLATION_APPROVED),
                eq(DeploymentEventStatus.SUCCESS),
                eq("friend"),
                any(), any(), any());
        verify(installationSyncService).syncRepos(42L);
    }

    @Test
    void approveInstallation_unknownInstallation_returns404() throws Exception {
        when(installationRepository.findByInstallationId(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/github/installations/99/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectInstallation_setsRejected() throws Exception {
        GitHubInstallation installation = installation(42L, "friend", InstallationStatus.PENDING, false);
        when(installationRepository.findByInstallationId(42L)).thenReturn(Optional.of(installation));
        when(installationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/github/installations/42/reject"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(GitHubInstallation.class);
        verify(installationRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getStatus()).isEqualTo(InstallationStatus.REJECTED);
    }

    @Test
    void listRepos_groupsByAccountAndComputesBadges() throws Exception {
        GitHubInstallation approved = installation(1L, "filip", InstallationStatus.APPROVED, false);
        GitHubInstallation pending = installation(2L, "friend", InstallationStatus.PENDING, false);
        when(installationRepository.findByStatus(InstallationStatus.APPROVED)).thenReturn(List.of(approved));

        GitHubRepo connected = new GitHubRepo();
        connected.setInstallationId(1L);
        connected.setFullName("filip/app-one");
        connected.setDefaultBranch("main");
        connected.setPrivate(false);

        GitHubRepo notDeployed = new GitHubRepo();
        notDeployed.setInstallationId(1L);
        notDeployed.setFullName("filip/app-two");
        notDeployed.setDefaultBranch("main");
        notDeployed.setPrivate(true);

        when(repoRepository.findByInstallationId(1L)).thenReturn(List.of(connected, notDeployed));

        Deployment deployment = new Deployment();
        deployment.setRepoUrl("https://github.com/filip/app-one");
        when(deploymentRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(deployment));

        mockMvc.perform(get("/api/github/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].account").value("filip"))
                .andExpect(jsonPath("$[0].repos[0].fullName").value("filip/app-one"))
                .andExpect(jsonPath("$[0].repos[0].badge").value("CONNECTED"))
                .andExpect(jsonPath("$[0].repos[1].fullName").value("filip/app-two"))
                .andExpect(jsonPath("$[0].repos[1].badge").value("NOT_DEPLOYED"))
                .andExpect(jsonPath("$[?(@.account == 'friend')]").isEmpty());
    }

    @Test
    void listRepos_appUnconfigured_returns409() throws Exception {
        when(configService.resolve()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/github/repos"))
                .andExpect(status().isConflict());
    }
}
