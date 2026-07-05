package dev.filipnikolov.vector.githubapp.service.impl;

import dev.filipnikolov.vector.envvar.crypto.EncryptionService;
import dev.filipnikolov.vector.github.app.GitHubAppAuthProvider;
import dev.filipnikolov.vector.github.app.GitHubAppJwt;
import dev.filipnikolov.vector.githubapp.model.GitHubAppConfig;
import dev.filipnikolov.vector.githubapp.repository.GitHubAppConfigRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class GitHubAppConfigServiceImpl implements GitHubAppConfigService {

    private final GitHubAppConfigRepository repository;
    private final EncryptionService encryptionService;
    private final RestClient.Builder restClientBuilder;
    private final String envAppId;
    private final String envPrivateKey;
    private final String envWebhookSecret;

    private volatile GitHubAppAuthProvider authProvider;

    public GitHubAppConfigServiceImpl(GitHubAppConfigRepository repository,
                                       EncryptionService encryptionService,
                                       RestClient.Builder restClientBuilder,
                                       @Value("${github.app.id:}") String envAppId,
                                       @Value("${github.app.private-key:}") String envPrivateKey,
                                       @Value("${github.app.webhook-secret:}") String envWebhookSecret) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.restClientBuilder = restClientBuilder;
        this.envAppId = envAppId;
        this.envPrivateKey = envPrivateKey;
        this.envWebhookSecret = envWebhookSecret;
    }

    @Override
    public Optional<AppCredentials> resolve() {
        if (!envAppId.isBlank() && !envPrivateKey.isBlank() && !envWebhookSecret.isBlank()) {
            PrivateKey key = GitHubAppJwt.parsePrivateKey(readPem(envPrivateKey));
            return Optional.of(new AppCredentials(envAppId, key, envWebhookSecret, null));
        }

        List<GitHubAppConfig> rows = repository.findAll();
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        GitHubAppConfig row = rows.get(0);
        String pem = encryptionService.decrypt(row.getPrivateKeyPemEnc());
        String webhookSecret = encryptionService.decrypt(row.getWebhookSecretEnc());
        PrivateKey key = GitHubAppJwt.parsePrivateKey(pem);
        return Optional.of(new AppCredentials(row.getAppId(), key, webhookSecret, row.getOwnerLogin()));
    }

    @Override
    public void store(String appId, String appSlug, String ownerLogin, String pem, String webhookSecret) {
        List<GitHubAppConfig> rows = repository.findAll();
        GitHubAppConfig config = rows.isEmpty() ? new GitHubAppConfig() : rows.get(0);
        config.setAppId(appId);
        config.setAppSlug(appSlug);
        config.setOwnerLogin(ownerLogin);
        config.setPrivateKeyPemEnc(encryptionService.encrypt(pem));
        config.setWebhookSecretEnc(encryptionService.encrypt(webhookSecret));
        if (config.getCreatedAt() == null) {
            config.setCreatedAt(LocalDateTime.now());
        }
        repository.save(config);
    }

    @Override
    public GitHubAppAuthProvider authProvider() {
        GitHubAppAuthProvider existing = authProvider;
        if (existing != null) {
            return existing;
        }
        AppCredentials credentials = resolve()
                .orElseThrow(() -> new IllegalStateException("GitHub App not configured"));
        GitHubAppAuthProvider created = new GitHubAppAuthProvider(credentials.appId(), credentials.key(), restClientBuilder);
        authProvider = created;
        return created;
    }

    private static String readPem(String privateKeyConfig) {
        Path path = Path.of(privateKeyConfig);
        if (Files.exists(path)) {
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read GitHub App private key file: " + path, e);
            }
        }
        if (privateKeyConfig.contains("BEGIN")) {
            return privateKeyConfig;
        }
        return new String(Base64.getDecoder().decode(privateKeyConfig), StandardCharsets.UTF_8);
    }
}
