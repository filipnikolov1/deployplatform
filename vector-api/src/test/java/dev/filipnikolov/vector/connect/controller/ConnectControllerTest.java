package dev.filipnikolov.vector.connect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.connect.dto.ConnectResponse;
import dev.filipnikolov.vector.connect.service.AppNameTakenException;
import dev.filipnikolov.vector.connect.service.AppNotFoundException;
import dev.filipnikolov.vector.connect.service.ConnectService;
import dev.filipnikolov.vector.connect.service.CustomWorkflowRedeployException;
import dev.filipnikolov.vector.connect.service.InstallationNotApprovedException;
import dev.filipnikolov.vector.connect.workflow.WiringResult;
import dev.filipnikolov.vector.github.client.dto.WorkflowRun;
import dev.filipnikolov.vector.githubapp.model.WorkflowMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConnectControllerTest {

    private MockMvc mockMvc;
    private ConnectService connectService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        connectService = mock(ConnectService.class);
        ConnectController controller = new ConnectController(connectService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private String requestBody() {
        return """
                { "repoFullName": "alice/shop", "branch": "main",
                  "modules": [ { "name": "shop-web", "path": "apps/web", "stack": "nextjs",
                                 "buildMode": "BUILDPACK", "port": 3000, "subdomain": null,
                                 "exposed": true, "env": {"KEY": "val"} } ],
                  "workflowMode": "MANAGED",
                  "provisionDb": false }
                """;
    }

    @Test
    void connect_omittedBooleansDefaultInsteadOf400() throws Exception {
        when(connectService.connect(any())).thenReturn(new ConnectResponse(null, java.util.List.of("shop-web"), null));

        String bodyWithoutBooleans = """
                { "repoFullName": "alice/shop", "branch": "main",
                  "modules": [ { "name": "shop-web", "path": "apps/web", "stack": "nextjs",
                                 "buildMode": "BUILDPACK", "port": 3000 } ],
                  "workflowMode": "MANAGED",
                  "provisionDb": false }
                """;

        mockMvc.perform(post("/api/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithoutBooleans))
                .andExpect(status().isOk());
    }

    @Test
    void connect_omittedBuildToolDefaultsToMaven() throws Exception {
        org.mockito.ArgumentCaptor<dev.filipnikolov.vector.connect.dto.ConnectRequest> captor =
                org.mockito.ArgumentCaptor.forClass(dev.filipnikolov.vector.connect.dto.ConnectRequest.class);
        when(connectService.connect(captor.capture()))
                .thenReturn(new ConnectResponse(null, java.util.List.of("shop-web"), null));

        String bodyWithoutBuildTool = """
                { "repoFullName": "alice/shop", "branch": "main",
                  "modules": [ { "name": "shop-web", "path": "apps/web", "stack": "springboot",
                                 "buildMode": "BUILDPACK", "port": 8080 } ],
                  "workflowMode": "MANAGED",
                  "provisionDb": false }
                """;

        mockMvc.perform(post("/api/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithoutBuildTool))
                .andExpect(status().isOk());

        assertThat(captor.getValue().modules().get(0).buildTool())
                .isEqualTo(dev.filipnikolov.vector.connect.detect.BuildTool.MAVEN);
    }

    @Test
    void connect_success_returns200() throws Exception {
        when(connectService.connect(any())).thenReturn(new ConnectResponse(null, java.util.List.of("shop-web"), null));

        mockMvc.perform(post("/api/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk());
    }

    @Test
    void connect_installationNotApproved_returns403() throws Exception {
        when(connectService.connect(any())).thenThrow(new InstallationNotApprovedException("not approved"));

        mockMvc.perform(post("/api/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void connect_appNameTaken_returns409() throws Exception {
        when(connectService.connect(any())).thenThrow(new AppNameTakenException("taken"));

        mockMvc.perform(post("/api/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isConflict());
    }

    @Test
    void connect_invalidRequest_returns400() throws Exception {
        when(connectService.connect(any())).thenThrow(new IllegalArgumentException("bad request"));

        mockMvc.perform(post("/api/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ciStatus_success_returnsRecentRuns() throws Exception {
        when(connectService.ciStatus("shop-web")).thenReturn(
                java.util.List.of(new WorkflowRun(1L, "completed", "success", "https://github.com/alice/shop/actions/runs/1")));

        mockMvc.perform(get("/api/connect/shop-web/ci-status"))
                .andExpect(status().isOk());
    }

    @Test
    void ciStatus_appNotFound_returns404() throws Exception {
        when(connectService.ciStatus("missing")).thenThrow(new AppNotFoundException("not found"));

        mockMvc.perform(get("/api/connect/missing/ci-status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void workflowMode_success_returns200() throws Exception {
        when(connectService.switchWorkflowMode(eq("shop-web"), eq(WorkflowMode.CUSTOM)))
                .thenReturn(new WiringResult(WorkflowMode.CUSTOM, ".github/workflows/vector-deploy.yml", "snippet"));

        mockMvc.perform(post("/api/connect/shop-web/workflow-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"CUSTOM\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void workflowMode_appNotFound_returns404() throws Exception {
        when(connectService.switchWorkflowMode(eq("missing"), any()))
                .thenThrow(new AppNotFoundException("not found"));

        mockMvc.perform(post("/api/connect/missing/workflow-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"MANAGED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void disconnect_success_returns200() throws Exception {
        doNothing().when(connectService).disconnect("shop-web");

        mockMvc.perform(post("/api/connect/shop-web/disconnect"))
                .andExpect(status().isOk());
    }

    @Test
    void disconnect_appNotFound_returns404() throws Exception {
        doThrow(new AppNotFoundException("not found")).when(connectService).disconnect("missing");

        mockMvc.perform(post("/api/connect/missing/disconnect"))
                .andExpect(status().isNotFound());
    }

    @Test
    void redeploy_managed_returns200() throws Exception {
        doNothing().when(connectService).redeploy("shop-web");

        mockMvc.perform(post("/api/connect/shop-web/redeploy"))
                .andExpect(status().isOk());
    }

    @Test
    void redeploy_customLane_returns409() throws Exception {
        doThrow(new CustomWorkflowRedeployException("custom lane")).when(connectService).redeploy("shop-web");

        mockMvc.perform(post("/api/connect/shop-web/redeploy"))
                .andExpect(status().isConflict());
    }
}
