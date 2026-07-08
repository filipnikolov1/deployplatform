package dev.filipnikolov.vector.connect.workflow.impl;

import dev.filipnikolov.vector.connect.detect.BuildMode;
import dev.filipnikolov.vector.connect.dto.ConnectRequest;
import dev.filipnikolov.vector.connect.workflow.BuildpackWorkflowRenderer;
import dev.filipnikolov.vector.connect.workflow.ModuleJob;
import dev.filipnikolov.vector.connect.workflow.WiringResult;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.github.client.GitHubAutomationClient;
import dev.filipnikolov.vector.github.client.dto.StackKind;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.model.WorkflowMode;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import dev.filipnikolov.vector.githubapp.service.GitHubAutomationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WorkflowWiringServiceImplTest {

    private static final String OUR_MARKER = "# vector:managed v1 app=vector-bot — managed by Deploy Platform; do not edit by hand\n";

    private GitHubAutomationService automationService;
    private GitHubAutomationClient automationClient;
    private GitHubAppConfigService configService;
    private GitHubRepoRepository repoRepository;
    private DeploymentEventService deploymentEventService;
    private BuildpackWorkflowRenderer renderer;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;

    private WorkflowWiringServiceImpl service;

    @BeforeEach
    void setUp() {
        automationService = mock(GitHubAutomationService.class);
        automationClient = mock(GitHubAutomationClient.class);
        configService = mock(GitHubAppConfigService.class);
        repoRepository = mock(GitHubRepoRepository.class);
        deploymentEventService = mock(DeploymentEventService.class);
        renderer = new BuildpackWorkflowRenderer();
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();

        when(automationService.forInstallation(1L)).thenReturn(automationClient);
        when(configService.resolve()).thenReturn(Optional.of(
                new GitHubAppConfigService.AppCredentials("1", (PrivateKey) null, "secret", "alice", "vector-bot")));
        dev.filipnikolov.vector.github.app.GitHubAppAuthProvider authProvider =
                mock(dev.filipnikolov.vector.github.app.GitHubAppAuthProvider.class);
        when(configService.authProvider()).thenReturn(authProvider);
        when(authProvider.tokenSupplier(1L)).thenReturn(() -> "test-token");
        when(repoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new WorkflowWiringServiceImpl(automationService, configService, repoRepository,
                deploymentEventService, renderer, restClientBuilder);
    }

    private GitHubRepo repo(String fullName, String defaultBranch) {
        GitHubRepo repo = new GitHubRepo();
        repo.setId(1L);
        repo.setFullName(fullName);
        repo.setInstallationId(1L);
        repo.setDefaultBranch(defaultBranch);
        return repo;
    }

    private ConnectRequest req(String repoFullName, String branch, WorkflowMode mode) {
        return new ConnectRequest(repoFullName, branch,
                List.of(new ConnectRequest.ModuleSelection("shop-web", "apps/web", "nextjs", BuildMode.BUILDPACK,
                        3000, null, true, false, null, null)),
                mode, false);
    }

    private List<ModuleJob> jobs() {
        return List.of(new ModuleJob("shop-web", "apps/web", BuildMode.BUILDPACK, "ghcr.io/alice/shop-web",
                StackKind.nextjs, false));
    }

    private void expectContentsGet(String owner, String repoName, String branch, String response, HttpStatus status) {
        server.expect(requestTo("https://api.github.com/repos/" + owner + "/" + repoName
                        + "/contents/.github/workflows/vector-deploy.yml?ref=" + branch))
                .andExpect(method(HttpMethod.GET))
                .andRespond(response == null ? withStatus(status) : withStatus(status).body(response).contentType(MediaType.APPLICATION_JSON));
    }

    private String contentResponse(String content) {
        return "{\"content\":\"" + Base64.getEncoder().encodeToString(content.getBytes()) + "\"}";
    }

    @Test
    void wire_foreignFileWithoutMarker_forcesCustomAndNeverWrites() {
        expectContentsGet("alice", "shop", "main", contentResponse("name: ci\non: push\n"), HttpStatus.OK);

        WiringResult result = service.wire(repo("alice/shop", "main"), req("alice/shop", "main", WorkflowMode.MANAGED), jobs());

        assertThat(result.mode()).isEqualTo(WorkflowMode.CUSTOM);
        assertThat(result.snippet()).isNotNull();
        verify(automationClient, never()).putFile(any(), any(), any(), any(), any(), any());
        verify(automationClient, never()).dispatchWorkflow(any(), any(), any(), any());
        server.verify();
    }

    @Test
    void wire_markerWithDifferentAppSlug_forcesCustom() {
        String foreignMarker = "# vector:managed v1 app=other-bot — managed by Deploy Platform; do not edit by hand\n";
        expectContentsGet("alice", "shop", "main", contentResponse(foreignMarker + "name: deploy\n"), HttpStatus.OK);

        WiringResult result = service.wire(repo("alice/shop", "main"), req("alice/shop", "main", WorkflowMode.MANAGED), jobs());

        assertThat(result.mode()).isEqualTo(WorkflowMode.CUSTOM);
        verify(automationClient, never()).putFile(any(), any(), any(), any(), any(), any());
        server.verify();
    }

    @Test
    void wire_noExistingFile_managedCreatesFileAndDispatches() {
        expectContentsGet("alice", "shop", "main", null, HttpStatus.NOT_FOUND);

        WiringResult result = service.wire(repo("alice/shop", "main"), req("alice/shop", "main", WorkflowMode.MANAGED), jobs());

        assertThat(result.mode()).isEqualTo(WorkflowMode.MANAGED);
        verify(automationClient).putFile(eq("alice"), eq("shop"), eq(".github/workflows/vector-deploy.yml"),
                any(), anyString(), eq("main"));
        verify(automationClient).dispatchWorkflow("alice", "shop", "vector-deploy.yml", "main");
        server.verify();
    }

    @Test
    void wire_existingFileWithOurMarker_managedUpdatesFile() {
        expectContentsGet("alice", "shop", "main", contentResponse(OUR_MARKER + "name: deploy\n"), HttpStatus.OK);

        WiringResult result = service.wire(repo("alice/shop", "main"), req("alice/shop", "main", WorkflowMode.MANAGED), jobs());

        assertThat(result.mode()).isEqualTo(WorkflowMode.MANAGED);
        verify(automationClient).putFile(eq("alice"), eq("shop"), eq(".github/workflows/vector-deploy.yml"),
                any(), anyString(), eq("main"));
        server.verify();
    }

    @Test
    void wire_putFileAlwaysTargetsDefaultBranchWhileDispatchUsesDeployBranch() {
        expectContentsGet("alice", "shop", "main", null, HttpStatus.NOT_FOUND);

        service.wire(repo("alice/shop", "main"), req("alice/shop", "develop", WorkflowMode.MANAGED), jobs());

        verify(automationClient).putFile(eq("alice"), eq("shop"), eq(".github/workflows/vector-deploy.yml"),
                any(), anyString(), eq("main"));
        verify(automationClient).dispatchWorkflow("alice", "shop", "vector-deploy.yml", "develop");
        server.verify();
    }

    @Test
    void wire_managed_neverCallsPutActionsSecret() {
        expectContentsGet("alice", "shop", "main", null, HttpStatus.NOT_FOUND);

        service.wire(repo("alice/shop", "main"), req("alice/shop", "main", WorkflowMode.MANAGED), jobs());

        verify(automationClient, never()).putActionsSecret(any(), any(), any(), any());
        server.verify();
    }

    @Test
    void wire_managed_dispatchesExactlyOnceAfterPutFile() {
        expectContentsGet("alice", "shop", "main", null, HttpStatus.NOT_FOUND);

        service.wire(repo("alice/shop", "main"), req("alice/shop", "main", WorkflowMode.MANAGED), jobs());

        verify(automationClient, times(1)).putFile(any(), any(), any(), any(), any(), any());
        verify(automationClient, times(1)).dispatchWorkflow(any(), any(), any(), any());
        server.verify();
    }

    @Test
    void wire_managed_persistsExpectedJobsMatchingRendererSnapshot() {
        expectContentsGet("alice", "shop", "main", null, HttpStatus.NOT_FOUND);
        GitHubRepo repoEntity = repo("alice/shop", "main");

        service.wire(repoEntity, req("alice/shop", "main", WorkflowMode.MANAGED), jobs());

        assertThat(repoEntity.getWorkflowMode()).isEqualTo(WorkflowMode.MANAGED);
        assertThat(repoEntity.getWorkflowTemplateVersion()).isNotNull();
        assertThat(repoEntity.getExpectedJobs()).contains("build-shop-web").contains("shop-web");
        verify(repoRepository).save(repoEntity);
        server.verify();
    }

    @Test
    void wire_managed_persistsModuleJobsSnapshot() {
        expectContentsGet("alice", "shop", "main", null, HttpStatus.NOT_FOUND);
        GitHubRepo repoEntity = repo("alice/shop", "main");

        service.wire(repoEntity, req("alice/shop", "main", WorkflowMode.MANAGED), jobs());

        assertThat(repoEntity.getModuleJobs()).contains("shop-web").contains("apps/web");
        server.verify();
    }

    @Test
    void wire_customMode_zeroWritesAndReturnsSnippet() {
        expectContentsGet("alice", "shop", "main", null, HttpStatus.NOT_FOUND);

        WiringResult result = service.wire(repo("alice/shop", "main"), req("alice/shop", "main", WorkflowMode.CUSTOM), jobs());

        assertThat(result.mode()).isEqualTo(WorkflowMode.CUSTOM);
        assertThat(result.snippet()).isNotNull();
        verify(automationClient, never()).putFile(any(), any(), any(), any(), any(), any());
        verify(automationClient, never()).dispatchWorkflow(any(), any(), any(), any());
        server.verify();
    }
}
