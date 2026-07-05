package dev.filipnikolov.vector.githubapp.service;

import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.githubapp.dto.InstallationPayload;
import dev.filipnikolov.vector.githubapp.dto.InstallationRepositoriesPayload;
import dev.filipnikolov.vector.githubapp.model.GitHubInstallation;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.model.InstallationStatus;
import dev.filipnikolov.vector.githubapp.repository.GitHubInstallationRepository;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.impl.InstallationSyncServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstallationSyncServiceTest {

    private GitHubInstallationRepository installationRepository;
    private GitHubRepoRepository repoRepository;
    private GitHubAppConfigService configService;
    private DeploymentEventService deploymentEventService;
    private InstallationSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        installationRepository = mock(GitHubInstallationRepository.class);
        repoRepository = mock(GitHubRepoRepository.class);
        configService = mock(GitHubAppConfigService.class);
        deploymentEventService = mock(DeploymentEventService.class);
        service = new InstallationSyncServiceImpl(
                installationRepository, repoRepository, configService, deploymentEventService);

        when(installationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void installationCreated_ownAccount_autoApproves() {
        when(configService.resolve()).thenReturn(Optional.of(
                new GitHubAppConfigService.AppCredentials("1", null, "secret", "filip")));
        when(installationRepository.findByInstallationId(42L)).thenReturn(Optional.empty());

        InstallationPayload payload = new InstallationPayload("created",
                new InstallationPayload.Installation(42L, new InstallationPayload.Account("filip", "User")));

        service.handleInstallation(payload);

        var captor = org.mockito.ArgumentCaptor.forClass(GitHubInstallation.class);
        verify(installationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InstallationStatus.APPROVED);
        verify(deploymentEventService, never()).record(eq(DeploymentEventType.INSTALLATION_PENDING), any(), any(), any(), any(), any());
    }

    @Test
    void installationCreated_otherAccount_pendingAndEventRecorded() {
        when(configService.resolve()).thenReturn(Optional.of(
                new GitHubAppConfigService.AppCredentials("1", null, "secret", "filip")));
        when(installationRepository.findByInstallationId(99L)).thenReturn(Optional.empty());

        InstallationPayload payload = new InstallationPayload("created",
                new InstallationPayload.Installation(99L, new InstallationPayload.Account("friend", "User")));

        service.handleInstallation(payload);

        var captor = org.mockito.ArgumentCaptor.forClass(GitHubInstallation.class);
        verify(installationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InstallationStatus.PENDING);
        verify(deploymentEventService).record(
                eq(DeploymentEventType.INSTALLATION_PENDING),
                eq(DeploymentEventStatus.SUCCESS),
                eq("friend"),
                any(), any(), any());
    }

    @Test
    void installationDeleted_removedAndReposCascade() {
        GitHubInstallation existing = new GitHubInstallation();
        existing.setInstallationId(7L);
        existing.setAccountLogin("friend");
        existing.setStatus(InstallationStatus.APPROVED);
        when(installationRepository.findByInstallationId(7L)).thenReturn(Optional.of(existing));

        InstallationPayload payload = new InstallationPayload("deleted",
                new InstallationPayload.Installation(7L, new InstallationPayload.Account("friend", "User")));

        service.handleInstallation(payload);

        var captor = org.mockito.ArgumentCaptor.forClass(GitHubInstallation.class);
        verify(installationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(InstallationStatus.REMOVED);
        verify(repoRepository).deleteByInstallationId(7L);
    }

    @Test
    void installationSuspended_setsSuspendedFlag() {
        GitHubInstallation existing = new GitHubInstallation();
        existing.setInstallationId(7L);
        existing.setAccountLogin("friend");
        existing.setStatus(InstallationStatus.APPROVED);
        existing.setSuspended(false);
        when(installationRepository.findByInstallationId(7L)).thenReturn(Optional.of(existing));

        InstallationPayload payload = new InstallationPayload("suspend",
                new InstallationPayload.Installation(7L, new InstallationPayload.Account("friend", "User")));

        service.handleInstallation(payload);

        var captor = org.mockito.ArgumentCaptor.forClass(GitHubInstallation.class);
        verify(installationRepository).save(captor.capture());
        assertThat(captor.getValue().isSuspended()).isTrue();
    }

    @Test
    void installationUnsuspended_clearsSuspendedFlag() {
        GitHubInstallation existing = new GitHubInstallation();
        existing.setInstallationId(7L);
        existing.setAccountLogin("friend");
        existing.setStatus(InstallationStatus.APPROVED);
        existing.setSuspended(true);
        when(installationRepository.findByInstallationId(7L)).thenReturn(Optional.of(existing));

        InstallationPayload payload = new InstallationPayload("unsuspend",
                new InstallationPayload.Installation(7L, new InstallationPayload.Account("friend", "User")));

        service.handleInstallation(payload);

        var captor = org.mockito.ArgumentCaptor.forClass(GitHubInstallation.class);
        verify(installationRepository).save(captor.capture());
        assertThat(captor.getValue().isSuspended()).isFalse();
    }

    @Test
    void installationRepositories_addedUpserted_removedDeleted() {
        InstallationRepositoriesPayload payload = new InstallationRepositoriesPayload(
                "added",
                new InstallationPayload.Installation(42L, new InstallationPayload.Account("filip", "User")),
                List.of(new InstallationRepositoriesPayload.RepoInfo("filip/app-one", false, "main")),
                List.of(new InstallationRepositoriesPayload.RepoInfo("filip/app-two", false, "main")));

        when(repoRepository.findByFullName("filip/app-one")).thenReturn(Optional.empty());
        when(repoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GitHubRepo toRemove = new GitHubRepo();
        toRemove.setFullName("filip/app-two");
        when(repoRepository.findByFullName("filip/app-two")).thenReturn(Optional.of(toRemove));

        service.handleInstallationRepositories(payload);

        var captor = org.mockito.ArgumentCaptor.forClass(GitHubRepo.class);
        verify(repoRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("filip/app-one");
        assertThat(captor.getValue().getInstallationId()).isEqualTo(42L);
        assertThat(captor.getValue().getDefaultBranch()).isEqualTo("main");

        verify(repoRepository).delete(toRemove);
    }
}
