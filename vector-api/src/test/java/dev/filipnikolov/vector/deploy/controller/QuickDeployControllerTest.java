package dev.filipnikolov.vector.deploy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.common.lock.ActionLockService;
import dev.filipnikolov.vector.config.ApiKeyAuthFilter;
import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import dev.filipnikolov.vector.envvar.service.EnvVarService;
import dev.filipnikolov.vector.events.DeploySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuickDeployControllerTest {

    private MockMvc mockMvc;
    private DeploymentService deploymentService;
    private DeploymentRepository deploymentRepository;
    private EnvVarService envVarService;
    private ActionLockService actionLockService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        deploymentService = mock(DeploymentService.class);
        deploymentRepository = mock(DeploymentRepository.class);
        envVarService = mock(EnvVarService.class);
        actionLockService = new ActionLockService();

        when(deploymentRepository.findByAppNameAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        doNothing().when(deploymentService).handleWebhookDeployAsync(any(), any());

        QuickDeployController controller = new QuickDeployController(
                deploymentService, deploymentRepository, envVarService, actionLockService);
        ReflectionTestUtils.setField(controller, "defaultContainerPort", 3000);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private String requestBody() {
        return """
                { "image": "ghcr.io/acme/widgets:latest", "appName": "widgets",
                  "port": 8080, "env": {"KEY": "val"}, "subdomain": null }
                """;
    }

    @Test
    void validRequest_returns202_withOperationId() throws Exception {
        mockMvc.perform(post("/api/deploy/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationId").exists());
    }

    @Test
    void validRequest_createsDeploymentWithQuickSource() throws Exception {
        mockMvc.perform(post("/api/deploy/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isAccepted());

        ArgumentCaptor<Deployment> captor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository).save(captor.capture());
        assertThat(captor.getValue().getDeploySource()).isEqualTo(DeploySource.QUICK);
        assertThat(captor.getValue().getAppName()).isEqualTo("widgets");
    }

    @Test
    void validRequest_invokesDeployPath() throws Exception {
        mockMvc.perform(post("/api/deploy/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isAccepted());

        ArgumentCaptor<CreateDeploymentRequest> captor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
        verify(deploymentService).handleWebhookDeployAsync(captor.capture(), any());
        assertThat(captor.getValue().appName()).isEqualTo("widgets");
        assertThat(captor.getValue().imageName()).isEqualTo("ghcr.io/acme/widgets:latest");
        assertThat(captor.getValue().containerPort()).isEqualTo(8080);
    }

    @Test
    void invalidAppName_returns400() throws Exception {
        String body = """
                { "image": "ghcr.io/acme/widgets:latest", "appName": "in valid name!",
                  "port": 8080 }
                """;

        mockMvc.perform(post("/api/deploy/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(deploymentService, never()).handleWebhookDeployAsync(any(), any());
    }

    @Test
    void asyncSubmissionRejected_returns503AndClosesLock() throws Exception {
        doThrow(new TaskRejectedException("executor saturated"))
                .when(deploymentService).handleWebhookDeployAsync(any(), any());

        mockMvc.perform(post("/api/deploy/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isServiceUnavailable());

        assertThat(actionLockService.tryLock("app:widgets")).isPresent();
    }

    @Test
    void validRequest_operationIdInResponseMatchesRequestPassedToService() throws Exception {
        String response = mockMvc.perform(post("/api/deploy/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String operationId = objectMapper.readTree(response).get("operationId").asText();

        ArgumentCaptor<CreateDeploymentRequest> captor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
        verify(deploymentService).handleWebhookDeployAsync(captor.capture(), any());
        assertThat(captor.getValue().operationId()).isEqualTo(operationId);
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        QuickDeployController controller = new QuickDeployController(
                deploymentService, deploymentRepository, envVarService, actionLockService);
        ReflectionTestUtils.setField(controller, "defaultContainerPort", 3000);

        MockMvc secureMockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilter(new ApiKeyAuthFilter("correct-key"))
                .build();

        secureMockMvc.perform(post("/api/deploy/quick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isUnauthorized());

        verify(deploymentService, never()).handleWebhookDeployAsync(any(), any());
    }
}
