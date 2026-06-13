package dev.filipnikolov.vector.selfapp.service;

import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.selfapp.model.PendingSelfUpdate;
import dev.filipnikolov.vector.selfapp.repository.PendingSelfUpdateRepository;
import dev.filipnikolov.vector.updater.UpdaterClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SelfAppUpdateServiceStalePendingTest {

    private SelfAppUpdateService service(PendingSelfUpdateRepository pendingRepo,
                                         DeploymentRepository deploymentRepo) {
        UpdaterClient updater = mock(UpdaterClient.class);
        when(updater.health()).thenReturn(true);
        return new SelfAppUpdateService(deploymentRepo,
                mock(DeploymentEventService.class), pendingRepo,
                mock(SelfAppUpdateExecutor.class), updater);
    }

    private DeploymentRepository repoWithUpdatableSelfApp() {
        Deployment d = new Deployment();
        d.setAppName("vector-analyzer");
        d.setSelfApp(true);
        d.setLatestKnownImage("repo/analyzer:git-new");
        d.setLatestKnownSha("newsha");
        DeploymentRepository repo = mock(DeploymentRepository.class);
        when(repo.findByAppNameAndDeletedAtIsNull("vector-analyzer")).thenReturn(Optional.of(d));
        return repo;
    }

    @Test
    void freshPendingStillBlocks() {
        PendingSelfUpdate fresh = new PendingSelfUpdate();
        fresh.setTriggeredAt(LocalDateTime.now().minusMinutes(2));
        PendingSelfUpdateRepository pendingRepo = mock(PendingSelfUpdateRepository.class);
        when(pendingRepo.findFirstByAppNameOrderByTriggeredAtDesc("vector-analyzer"))
                .thenReturn(Optional.of(fresh));

        assertThatThrownBy(() -> service(pendingRepo, repoWithUpdatableSelfApp())
                .triggerUpdate("vector-analyzer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in progress");
    }

    @Test
    void stalePendingIsPurgedAndUpdateProceeds() {
        PendingSelfUpdate stale = new PendingSelfUpdate();
        stale.setTriggeredAt(LocalDateTime.now().minusHours(2));
        PendingSelfUpdateRepository pendingRepo = mock(PendingSelfUpdateRepository.class);
        when(pendingRepo.findFirstByAppNameOrderByTriggeredAtDesc("vector-analyzer"))
                .thenReturn(Optional.of(stale));

        service(pendingRepo, repoWithUpdatableSelfApp()).triggerUpdate("vector-analyzer");

        verify(pendingRepo).delete(stale);
        verify(pendingRepo).save(any(PendingSelfUpdate.class)); // new pending row created
    }
}
