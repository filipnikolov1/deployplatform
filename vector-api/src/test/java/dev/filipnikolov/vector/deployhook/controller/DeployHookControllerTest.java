package dev.filipnikolov.vector.deployhook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.common.lock.ActionLockService;
import dev.filipnikolov.vector.deployhook.auth.service.DeployHookAuthService;
import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployHookControllerTest {

    private DeploymentService deploymentService;
    private DeployHookAuthService deployHookAuthService;
    private ActionLockService actionLockService;
    private DeployHookController controller;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        deploymentService = mock(DeploymentService.class);
        deployHookAuthService = mock(DeployHookAuthService.class);
        actionLockService = new ActionLockService();
        mapper = new ObjectMapper();
        controller = new DeployHookController(deploymentService, deployHookAuthService, actionLockService, mapper);
        ReflectionTestUtils.setField(controller, "defaultContainerPort", 3000);

        when(deployHookAuthService.isValidSignature(any(), any())).thenReturn(true);
        when(deployHookAuthService.registerSignatureOnce(any())).thenReturn(true);
        doNothing().when(deploymentService).handleWebhookDeployAsync(any(), any());
    }

    @Test
    void validPayload_returns202_andAllCommitFieldsFlow() throws Exception {
        Map<String, Object> body = baseBody("ok-app");
        body.put("repo_url", "https://example.com/u/r");
        body.put("branch", "main");
        body.put("commit_sha", "abc123");
        body.put("commit_message", "msg");
        body.put("commit_author", "filip");
        body.put("commit_timestamp", 1_700_000_000L);
        body.put("port", 4000);

        ResponseEntity<?> response = controller.handleDeploy("sha256=x", "manual", mapper.writeValueAsString(body));
        assertThat(response.getStatusCode().value()).isEqualTo(202);

        ArgumentCaptor<CreateDeploymentRequest> captor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
        verify(deploymentService).handleWebhookDeployAsync(captor.capture(), any());
        CreateDeploymentRequest req = captor.getValue();
        assertThat(req.appName()).isEqualTo("ok-app");
        assertThat(req.repoUrl()).isEqualTo("https://example.com/u/r");
        assertThat(req.imageName()).isEqualTo("registry/test:latest");
        assertThat(req.containerPort()).isEqualTo(4000);
        assertThat(req.branch()).isEqualTo("main");
        assertThat(req.commitSha()).isEqualTo("abc123");
        assertThat(req.commitMessage()).isEqualTo("msg");
        assertThat(req.commitAuthor()).isEqualTo("filip");
        assertThat(req.commitTimestamp()).isNotNull();
    }

    @Test
    void badSignature_returns401() throws Exception {
        when(deployHookAuthService.isValidSignature(any(), any())).thenReturn(false);
        ResponseEntity<?> response = controller.handleDeploy("sha256=bad", null, mapper.writeValueAsString(baseBody("app")));
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void replayedSignature_returns409() throws Exception {
        when(deployHookAuthService.registerSignatureOnce(any())).thenReturn(false);
        ResponseEntity<?> response = controller.handleDeploy("sha256=x", null, mapper.writeValueAsString(baseBody("app")));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void malformedJson_throwsBadRequest() {
        assertThatThrownBy(() -> controller.handleDeploy("sha256=x", null, "not json"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void timestampAsString_throwsBadRequest() {
        // Typed Long → Jackson binding error → 400 (was a ClassCastException → 500 before 0A).
        assertThatThrownBy(() -> controller.handleDeploy("sha256=x", null,
                "{\"timestamp\":\"1\",\"image\":\"img\",\"app_name\":\"app\"}"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void portAsString_throwsBadRequest() {
        assertThatThrownBy(() -> controller.handleDeploy("sha256=x", null,
                "{\"timestamp\":" + System.currentTimeMillis() + ",\"image\":\"img\",\"app_name\":\"app\",\"port\":\"abc\"}"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void missingAppName_returns400() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", System.currentTimeMillis());
        body.put("image", "registry/test:latest");
        ResponseEntity<?> response = controller.handleDeploy("sha256=x", null, mapper.writeValueAsString(body));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void invalidAppNamePattern_returns400() throws Exception {
        Map<String, Object> body = baseBody("BAD APP!");
        ResponseEntity<?> response = controller.handleDeploy("sha256=x", null, mapper.writeValueAsString(body));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void staleTimestamp_returns401() throws Exception {
        Map<String, Object> body = baseBody("app");
        body.put("timestamp", System.currentTimeMillis() - (10 * 60 * 1000)); // 10 min old
        ResponseEntity<?> response = controller.handleDeploy("sha256=x", null, mapper.writeValueAsString(body));
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void lockHeld_returns409() throws Exception {
        // Pre-acquire the lock to simulate an in-flight deploy for the same app.
        actionLockService.tryLock("app:locked-app");

        ResponseEntity<?> response = controller.handleDeploy("sha256=x", null, mapper.writeValueAsString(baseBody("locked-app")));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    private Map<String, Object> baseBody(String appName) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", System.currentTimeMillis());
        body.put("image", "registry/test:latest");
        body.put("app_name", appName);
        return body;
    }
}
