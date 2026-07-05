package dev.filipnikolov.vector.githubapp.service;

import dev.filipnikolov.vector.github.app.GitHubAppAuthProvider;
import dev.filipnikolov.vector.github.client.GitHubAutomationClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GitHubAutomationService {
    private final GitHubAppConfigService config;

    public GitHubAutomationService(GitHubAppConfigService config) {
        this.config = config;
    }

    /** Per-installation write client; throws AppNotConfiguredException when the App is dark. */
    public GitHubAutomationClient forInstallation(long installationId) {
        GitHubAppAuthProvider provider;
        try {
            provider = config.authProvider();
        } catch (IllegalStateException e) {
            throw new AppNotConfiguredException("GitHub App not configured", e);
        }
        return new GitHubAutomationClient(provider.tokenSupplier(installationId), RestClient.builder());
    }
}
