package dev.filipnikolov.vector.connect.controller;

import dev.filipnikolov.vector.connect.db.AppsPostgresProvisioner;
import dev.filipnikolov.vector.connect.db.DatabaseSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatabaseControllerTest {

    private MockMvc mockMvc;
    private AppsPostgresProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = mock(AppsPostgresProvisioner.class);
        DatabaseController controller = new DatabaseController(provisioner);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listReturnsAllDatabases() throws Exception {
        when(provisioner.list()).thenReturn(List.of(
                new DatabaseSummary("shop-api", "app_shop_api", false, LocalDateTime.now())));

        mockMvc.perform(get("/api/databases"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteWithoutOrphanReturns409() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("not orphaned"))
                .when(provisioner).drop(eq("app_shop_api"), eq("app_shop_api"));

        mockMvc.perform(delete("/api/databases/app_shop_api").param("confirm", "app_shop_api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteWithMismatchedConfirmReturns400() throws Exception {
        mockMvc.perform(delete("/api/databases/app_shop_api").param("confirm", "wrong-name")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(provisioner, never()).drop(eq("app_shop_api"), eq("wrong-name"));
    }

    @Test
    void deleteWithMatchingConfirmOnOrphanedSucceeds() throws Exception {
        mockMvc.perform(delete("/api/databases/app_shop_api").param("confirm", "app_shop_api")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(provisioner).drop("app_shop_api", "app_shop_api");
    }
}
