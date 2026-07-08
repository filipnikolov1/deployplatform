package dev.filipnikolov.vector.project.controller;

import dev.filipnikolov.vector.project.service.ProjectEnvService;
import dev.filipnikolov.vector.project.service.ProjectEnvVarView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectEnvControllerTest {

    private MockMvc mockMvc;
    private ProjectEnvService projectEnvService;

    @BeforeEach
    void setUp() {
        projectEnvService = mock(ProjectEnvService.class);
        ProjectEnvController controller = new ProjectEnvController(projectEnvService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listReturnsKeysAndValues() throws Exception {
        when(projectEnvService.listProjectEnvVars(1L))
                .thenReturn(List.of(new ProjectEnvVarView("API_KEY", "secret")));

        mockMvc.perform(get("/api/projects/1/env"))
                .andExpect(status().isOk());
    }

    @Test
    void putUpsertsEnvVar() throws Exception {
        mockMvc.perform(put("/api/projects/1/env")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"API_KEY\",\"value\":\"secret\"}"))
                .andExpect(status().isOk());

        verify(projectEnvService).setProjectEnvVar(1L, "API_KEY", "secret");
    }

    @Test
    void deleteRemovesEnvVar() throws Exception {
        mockMvc.perform(delete("/api/projects/1/env/API_KEY"))
                .andExpect(status().isNoContent());

        verify(projectEnvService).deleteProjectEnvVar(1L, "API_KEY");
    }
}
