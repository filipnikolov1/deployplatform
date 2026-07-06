package dev.filipnikolov.vector.connect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.connect.dto.ConnectResponse;
import dev.filipnikolov.vector.connect.service.AppNameTakenException;
import dev.filipnikolov.vector.connect.service.ConnectService;
import dev.filipnikolov.vector.connect.service.InstallationNotApprovedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
}
