package dev.filipnikolov.vector.deployhook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.common.lock.ActionLockService;
import dev.filipnikolov.vector.connect.deploy.BuildEventService;
import dev.filipnikolov.vector.deployhook.auth.service.DeployHookAuthService;
import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import dev.filipnikolov.vector.githubapp.service.GitHubAppWebhookAuthService;
import dev.filipnikolov.vector.githubapp.service.InstallationSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubWebhookControllerLockLeakTest {

    private DeploymentService deploymentService;
    private DeployHookAuthService deployHookAuthService;
    private ActionLockService actionLockService;
    private GitHubWebhookController controller;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        deploymentService = mock(DeploymentService.class);
        deployHookAuthService = mock(DeployHookAuthService.class);
        actionLockService = new ActionLockService();
        mapper = new ObjectMapper();
        controller = new GitHubWebhookController(deploymentService, deployHookAuthService, actionLockService,
                mock(GitHubAppWebhookAuthService.class), mock(InstallationSyncService.class),
                mock(BuildEventService.class), mapper);
        ReflectionTestUtils.setField(controller, "defaultContainerPort", 3000);

        when(deployHookAuthService.isValidSignature(any(), any())).thenReturn(true);
        when(deployHookAuthService.registerSignatureOnce(any())).thenReturn(true);
    }

    @Test
    void taskRejection_releasesLockAndReturns503() throws Exception {
        doThrow(new TaskRejectedException("queue full"))
                .when(deploymentService).handleWebhookDeployAsync(any(), any());

        String body = signedPayload("rejected-app");

        ResponseEntity<?> first = controller.handleDeploy("sha256=ignored", null, body);
        assertThat(first.getStatusCode().value()).isEqualTo(503);

        // Lock must be released — a fresh deploy for the same app must be acceptable.
        assertThat(actionLockService.tryLock("app:rejected-app"))
                .as("lock for rejected app must be reacquirable after rejection")
                .isPresent();
    }

    @Test
    void successfulDispatch_returns202() throws Exception {
        doNothing().when(deploymentService).handleWebhookDeployAsync(any(), any());

        ResponseEntity<?> response = controller.handleDeploy(
                "sha256=ignored", null, signedPayload("ok-app"));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
    }

    private String signedPayload(String appName) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "timestamp", System.currentTimeMillis(),
                "image", "registry/test:latest",
                "app_name", appName
        ));
    }
}
