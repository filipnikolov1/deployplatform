package dev.filipnikolov.vector.ai.controller;

import dev.filipnikolov.vector.ai.service.OllamaService;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import dev.filipnikolov.vector.docker.service.DockerService;
import dev.filipnikolov.vector.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiController.class,
        excludeAutoConfiguration = {
            org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration.class
        })
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OllamaService ollamaService;

    @MockitoBean
    private DockerService dockerService;

    @MockitoBean
    private DeploymentService deploymentService;

    @Test
    void postAsk_returnsModelAnswerAsPlainText() throws Exception {
        when(ollamaService.ask("why is the sky blue?"))
                .thenReturn("Because of Rayleigh scattering.");

        mockMvc.perform(post("/api/ai/ask")
                        .contentType("text/plain")
                        .content("why is the sky blue?"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("Because of Rayleigh scattering."));
    }

    @Test
    void postAsk_returns400_whenPromptBlank() throws Exception {
        mockMvc.perform(post("/api/ai/ask")
                        .contentType("text/plain")
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postAsk_returns400_whenPromptExceedsMaxLength() throws Exception {
        String tooLong = "a".repeat(4001);
        mockMvc.perform(post("/api/ai/ask")
                        .contentType("text/plain")
                        .content(tooLong))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAnalyzeLogs_returnsAnalysisForKnownApp() throws Exception {
        Deployment deployment = new Deployment();
        deployment.setAppName("myapp");
        when(deploymentService.getDeployment("myapp")).thenReturn(deployment);
        when(dockerService.getContainerLogs(eq("myapp"), any(Integer.class)))
                .thenReturn(List.of("line 1", "line 2 ERROR NullPointer"));
        when(ollamaService.analyzeLog("line 1\nline 2 ERROR NullPointer"))
                .thenReturn("Your app threw a NullPointerException.");

        mockMvc.perform(get("/api/ai/logs/analyze").param("app", "myapp"))
                .andExpect(status().isOk())
                .andExpect(content().string("Your app threw a NullPointerException."));
    }

    @Test
    void getAnalyzeLogs_returns404_whenAppUnknown() throws Exception {
        when(deploymentService.getDeployment("ghost"))
                .thenThrow(new ResourceNotFoundException("Deployment not found: ghost"));

        mockMvc.perform(get("/api/ai/logs/analyze").param("app", "ghost"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAnalyzeLogs_shortCircuits_whenNoLogsAvailable() throws Exception {
        Deployment deployment = new Deployment();
        deployment.setAppName("quietapp");
        when(deploymentService.getDeployment("quietapp")).thenReturn(deployment);
        when(dockerService.getContainerLogs(eq("quietapp"), any(Integer.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/ai/logs/analyze").param("app", "quietapp"))
                .andExpect(status().isOk())
                .andExpect(content().string("No logs available for this app yet."));
    }
}
