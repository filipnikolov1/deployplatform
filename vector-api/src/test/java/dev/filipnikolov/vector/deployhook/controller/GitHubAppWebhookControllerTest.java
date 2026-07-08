package dev.filipnikolov.vector.deployhook.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.common.lock.ActionLockService;
import dev.filipnikolov.vector.connect.deploy.BuildEventService;
import dev.filipnikolov.vector.deployhook.auth.service.DeployHookAuthService;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import dev.filipnikolov.vector.githubapp.dto.InstallationPayload;
import dev.filipnikolov.vector.githubapp.dto.InstallationRepositoriesPayload;
import dev.filipnikolov.vector.githubapp.service.GitHubAppWebhookAuthService;
import dev.filipnikolov.vector.githubapp.service.InstallationSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubAppWebhookControllerTest {

    private MockMvc mockMvc;
    private GitHubAppWebhookAuthService gitHubAppWebhookAuthService;
    private InstallationSyncService installationSyncService;
    private BuildEventService buildEventService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeployHookAuthService deployHookAuthService = mock(DeployHookAuthService.class);
        ActionLockService actionLockService = new ActionLockService();
        gitHubAppWebhookAuthService = mock(GitHubAppWebhookAuthService.class);
        installationSyncService = mock(InstallationSyncService.class);
        buildEventService = mock(BuildEventService.class);
        mapper = new ObjectMapper();

        GitHubWebhookController controller = new GitHubWebhookController(
                deploymentService, deployHookAuthService, actionLockService,
                gitHubAppWebhookAuthService, installationSyncService, buildEventService, mapper);
        ReflectionTestUtils.setField(controller, "defaultContainerPort", 3000);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(gitHubAppWebhookAuthService.isValidSignature(any(), any())).thenReturn(true);
        when(gitHubAppWebhookAuthService.registerDeliveryOnce(any())).thenReturn(true);
    }

    @Test
    void validSignature_returns200() throws Exception {
        mockMvc.perform(post("/api/github/app-webhook")
                        .header("X-Hub-Signature-256", "sha256=validhash")
                        .header("X-GitHub-Event", "ping")
                        .header("X-GitHub-Delivery", "guid-1")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void badSignature_returns401() throws Exception {
        when(gitHubAppWebhookAuthService.isValidSignature(any(), any())).thenReturn(false);

        mockMvc.perform(post("/api/github/app-webhook")
                        .header("X-Hub-Signature-256", "sha256=bad")
                        .header("X-GitHub-Event", "ping")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingSignature_returns401() throws Exception {
        when(gitHubAppWebhookAuthService.isValidSignature(any(), any())).thenReturn(false);

        mockMvc.perform(post("/api/github/app-webhook")
                        .header("X-Hub-Signature-256", "")
                        .header("X-GitHub-Event", "ping")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pingEvent_returns200NoOp() throws Exception {
        mockMvc.perform(post("/api/github/app-webhook")
                        .header("X-Hub-Signature-256", "sha256=validhash")
                        .header("X-GitHub-Event", "ping")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void installationEvent_routedToSyncServiceWithParsedPayload() throws Exception {
        String body = """
                {"action":"created","installation":{"id":42,"account":{"login":"filip","type":"User"}}}
                """;

        mockMvc.perform(post("/api/github/app-webhook")
                        .header("X-Hub-Signature-256", "sha256=validhash")
                        .header("X-GitHub-Event", "installation")
                        .content(body))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(InstallationPayload.class);
        verify(installationSyncService).handleInstallation(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().action()).isEqualTo("created");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().installation().id()).isEqualTo(42L);
    }

    @Test
    void installationRepositoriesEvent_routedToSyncServiceWithParsedPayload() throws Exception {
        String body = """
                {"action":"added","installation":{"id":42,"account":{"login":"filip","type":"User"}},
                 "repositories_added":[{"full_name":"filip/app-one","private":false,"default_branch":"main"}],
                 "repositories_removed":[]}
                """;

        mockMvc.perform(post("/api/github/app-webhook")
                        .header("X-Hub-Signature-256", "sha256=validhash")
                        .header("X-GitHub-Event", "installation_repositories")
                        .content(body))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(InstallationRepositoriesPayload.class);
        verify(installationSyncService).handleInstallationRepositories(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().repositoriesAdded().get(0).fullName())
                .isEqualTo("filip/app-one");
    }

    @Test
    void pushEvent_routedToBuildEventServiceWithParsedPayload() throws Exception {
        String body = "{\"ref\":\"refs/heads/main\"}";

        mockMvc.perform(post("/api/github/app-webhook")
                        .header("X-Hub-Signature-256", "sha256=validhash")
                        .header("X-GitHub-Event", "push")
                        .content(body))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(buildEventService).handlePush(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().get("ref").asText()).isEqualTo("refs/heads/main");
    }

    @Test
    void workflowRunEvent_routedToBuildEventServiceWithParsedPayload() throws Exception {
        String body = "{\"action\":\"requested\"}";

        mockMvc.perform(post("/api/github/app-webhook")
                        .header("X-Hub-Signature-256", "sha256=validhash")
                        .header("X-GitHub-Event", "workflow_run")
                        .content(body))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(buildEventService).handleWorkflowRun(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().get("action").asText()).isEqualTo("requested");
    }

    @Test
    void workflowJobEvent_routedToBuildEventServiceWithParsedPayload() throws Exception {
        String body = "{\"action\":\"queued\"}";

        mockMvc.perform(post("/api/github/app-webhook")
                        .header("X-Hub-Signature-256", "sha256=validhash")
                        .header("X-GitHub-Event", "workflow_job")
                        .content(body))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(buildEventService).handleWorkflowJob(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().get("action").asText()).isEqualTo("queued");
    }

    @Test
    void duplicateDelivery_shortCircuitsBeforeRouting() throws Exception {
        when(gitHubAppWebhookAuthService.registerDeliveryOnce("guid-dup")).thenReturn(false);

        mockMvc.perform(post("/api/github/app-webhook")
                        .header("X-Hub-Signature-256", "sha256=validhash")
                        .header("X-GitHub-Event", "installation")
                        .header("X-GitHub-Delivery", "guid-dup")
                        .content("{\"action\":\"created\",\"installation\":{\"id\":1,\"account\":{\"login\":\"filip\",\"type\":\"User\"}}}"))
                .andExpect(status().isOk());

        verify(installationSyncService, times(0)).handleInstallation(any());
    }
}
