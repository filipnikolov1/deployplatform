package dev.filipnikolov.vector.githubapp.controller;

import dev.filipnikolov.vector.config.DomainConfig;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubAppSetupControllerTest {

    private MockMvc mockMvc;
    private GitHubAppConfigService configService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        configService = mock(GitHubAppConfigService.class);
        DomainConfig domainConfig = new DomainConfig("filipnikolov.dev", "apps", "", "");

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        GitHubAppSetupController controller = new GitHubAppSetupController(configService, domainConfig, builder);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void manifest_returnsPostUrlAndExactManifestShape() throws Exception {
        when(configService.resolve()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/github/app-manifest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postUrl").value(org.hamcrest.Matchers.startsWith("https://github.com/settings/apps/new?state=")))
                .andExpect(jsonPath("$.manifest.name").value(org.hamcrest.Matchers.startsWith("deploy-platform-")))
                .andExpect(jsonPath("$.manifest.url").value("https://deploy.filipnikolov.dev"))
                .andExpect(jsonPath("$.manifest.hook_attributes.url").value("https://api.deploy.filipnikolov.dev/api/github/app-webhook"))
                .andExpect(jsonPath("$.manifest.redirect_url").value("https://deploy.filipnikolov.dev/setup/github-app/callback"))
                .andExpect(jsonPath("$.manifest.public").value(true))
                .andExpect(jsonPath("$.manifest.default_permissions.contents").value("write"))
                .andExpect(jsonPath("$.manifest.default_permissions.workflows").value("write"))
                .andExpect(jsonPath("$.manifest.default_permissions.secrets").value("write"))
                .andExpect(jsonPath("$.manifest.default_permissions.actions").value("write"))
                .andExpect(jsonPath("$.manifest.default_permissions.metadata").value("read"))
                .andExpect(jsonPath("$.manifest.default_permissions.pull_requests").value("read"))
                .andExpect(jsonPath("$.manifest.default_permissions.packages").value("read"))
                .andExpect(jsonPath("$.manifest.default_events[0]").value("push"))
                .andExpect(jsonPath("$.manifest.default_events[1]").value("pull_request"))
                .andExpect(jsonPath("$.manifest.default_events[2]").value("workflow_run"))
                .andExpect(jsonPath("$.manifest.default_events[3]").value("workflow_job"));
    }

    @Test
    void exchange_happyPath_storesConfigAndReturnsSlugAndOwner() throws Exception {
        when(configService.resolve()).thenReturn(Optional.empty());

        mockServer.expect(requestTo("https://api.github.com/app-manifests/abc123/conversions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"id":99,"slug":"deploy-platform-abcd1234","owner":{"login":"filip"},
                         "pem":"-----BEGIN RSA PRIVATE KEY-----\\nabc\\n-----END RSA PRIVATE KEY-----",
                         "webhook_secret":"whsecret"}
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/github/app-manifest/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"abc123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("deploy-platform-abcd1234"))
                .andExpect(jsonPath("$.ownerLogin").value("filip"));

        mockServer.verify();
        verify(configService).store("99", "deploy-platform-abcd1234", "filip",
                "-----BEGIN RSA PRIVATE KEY-----\nabc\n-----END RSA PRIVATE KEY-----", "whsecret");
    }

    @Test
    void exchange_envAlreadyConfigured_returns409() throws Exception {
        when(configService.resolve()).thenReturn(Optional.of(
                new GitHubAppConfigService.AppCredentials("1", null, "secret", "filip")));

        mockMvc.perform(post("/api/github/app-manifest/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"abc123\"}"))
                .andExpect(status().isConflict());

        verify(configService, never()).store(any(), any(), any(), any(), any());
    }
}
