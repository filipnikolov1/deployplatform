package dev.filipnikolov.vector.githubapp.service;

import dev.filipnikolov.vector.github.app.GitHubAppAuthProvider;
import dev.filipnikolov.vector.github.client.GitHubAutomationClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubAutomationServiceTest {

    @Test
    void forInstallation_appNotConfigured_throwsTypedException() {
        GitHubAppConfigService config = mock(GitHubAppConfigService.class);
        when(config.authProvider()).thenThrow(new IllegalStateException("GitHub App not configured"));

        GitHubAutomationService service = new GitHubAutomationService(config);

        assertThatThrownBy(() -> service.forInstallation(42L))
                .isInstanceOf(AppNotConfiguredException.class);
    }

    @Test
    void forInstallation_configured_returnsClientUsingProviderTokenSupplier() {
        GitHubAppConfigService config = mock(GitHubAppConfigService.class);
        GitHubAppAuthProvider provider = mock(GitHubAppAuthProvider.class);
        when(config.authProvider()).thenReturn(provider);
        when(provider.tokenSupplier(42L)).thenReturn(() -> "installation-token");

        GitHubAutomationService service = new GitHubAutomationService(config);

        GitHubAutomationClient client = service.forInstallation(42L);

        assertThat(client).isNotNull();
    }
}
