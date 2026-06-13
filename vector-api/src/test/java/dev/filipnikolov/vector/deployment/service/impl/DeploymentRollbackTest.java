package dev.filipnikolov.vector.deployment.service.impl;

import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.model.DeploymentEvent;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.DeploymentStatus;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentRollbackTest {

    @Test
    void finalizeRollbackUpdatesCommitMetadataAndPin() {
        DeploymentRepository repo = mock(DeploymentRepository.class);
        DeploymentEventService events = mock(DeploymentEventService.class);
        DeploymentTransactionHelper helper = new DeploymentTransactionHelper(
                repo, events, mock(dev.filipnikolov.vector.monitoring.service.NotificationService.class));

        Deployment d = new Deployment();
        d.setAppName("myapp");
        d.setCommitSha("old-sha");
        when(repo.findByAppNameAndDeletedAtIsNull("myapp")).thenReturn(Optional.of(d));
        when(events.record(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new DeploymentEvent());

        DeploymentEvent target = new DeploymentEvent();
        target.setAppName("myapp");
        target.setImageName("repo/app:git-target");
        target.setCommitSha("target-sha");
        target.setCommitMessage("the good commit");
        target.setCommitAuthor("filip");
        target.setBranch("main");

        CreateDeploymentRequest ctx = new CreateDeploymentRequest(
                "myapp", null, "repo/app:git-target", 3000,
                "main", "target-sha", "the good commit", "filip", null, null,
                dev.filipnikolov.vector.events.TriggerSource.ROLLBACK);

        Deployment result = helper.finalizeRollback("myapp", target, ctx, "old-sha", "op-1");

        assertThat(result.getImageName()).isEqualTo("repo/app:git-target");
        assertThat(result.getPinnedImage()).isEqualTo("repo/app:git-target");
        assertThat(result.getCommitSha()).isEqualTo("target-sha");
        assertThat(result.getCommitMessage()).isEqualTo("the good commit");
        assertThat(result.getCommitAuthor()).isEqualTo("filip");
        assertThat(result.getBranch()).isEqualTo("main");
        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.RUNNING);
        verify(events).record(eq(DeploymentEventType.MANUAL_ROLLBACK),
                eq(DeploymentEventStatus.SUCCESS), eq("myapp"), eq(ctx), isNull(), isNull(), eq("op-1"));
    }
}
