package dev.filipnikolov.vector.githubapp.service;

import dev.filipnikolov.vector.github.app.GitHubAppAuthProvider;

import java.security.PrivateKey;
import java.util.Optional;

public interface GitHubAppConfigService {

    record AppCredentials(String appId, PrivateKey key, String webhookSecret, String ownerLogin) {
    }

    Optional<AppCredentials> resolve();

    void store(String appId, String appSlug, String ownerLogin, String pem, String webhookSecret);

    GitHubAppAuthProvider authProvider();
}
