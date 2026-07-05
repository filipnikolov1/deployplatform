package dev.filipnikolov.vector.githubapp.service;

import dev.filipnikolov.vector.envvar.crypto.EncryptionService;
import dev.filipnikolov.vector.github.app.GitHubAppJwt;
import dev.filipnikolov.vector.githubapp.model.GitHubAppConfig;
import dev.filipnikolov.vector.githubapp.repository.GitHubAppConfigRepository;
import dev.filipnikolov.vector.githubapp.service.impl.GitHubAppConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubAppConfigServiceTest {

    private static final String TEST_PEM = generateTestPem();

    private static String generateTestPem() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            String encoded = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
            return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void resolve_envVarsSet_usesEnvWithoutTouchingDb() {
        GitHubAppConfigRepository repository = mock(GitHubAppConfigRepository.class);
        EncryptionService encryptionService = mock(EncryptionService.class);

        GitHubAppConfigServiceImpl service = new GitHubAppConfigServiceImpl(
                repository, encryptionService, RestClient.builder(),
                "123", TEST_PEM, "env-secret");

        Optional<GitHubAppConfigService.AppCredentials> result = service.resolve();

        assertThat(result).isPresent();
        assertThat(result.get().appId()).isEqualTo("123");
        assertThat(result.get().webhookSecret()).isEqualTo("env-secret");
        assertThat(GitHubAppJwt.create("123", result.get().key(), java.time.Instant.now())).isNotBlank();
    }

    @Test
    void resolve_dbRowOnly_decrypts() {
        GitHubAppConfigRepository repository = mock(GitHubAppConfigRepository.class);
        EncryptionService encryptionService = mock(EncryptionService.class);

        GitHubAppConfig row = new GitHubAppConfig();
        row.setAppId("456");
        row.setOwnerLogin("filip");
        row.setPrivateKeyPemEnc("enc-pem");
        row.setWebhookSecretEnc("enc-secret");
        when(repository.findAll()).thenReturn(java.util.List.of(row));
        when(encryptionService.decrypt("enc-pem")).thenReturn(TEST_PEM);
        when(encryptionService.decrypt("enc-secret")).thenReturn("db-secret");

        GitHubAppConfigServiceImpl service = new GitHubAppConfigServiceImpl(
                repository, encryptionService, RestClient.builder(), "", "", "");

        Optional<GitHubAppConfigService.AppCredentials> result = service.resolve();

        assertThat(result).isPresent();
        assertThat(result.get().appId()).isEqualTo("456");
        assertThat(result.get().webhookSecret()).isEqualTo("db-secret");
        assertThat(result.get().ownerLogin()).isEqualTo("filip");
    }

    @Test
    void resolve_neitherEnvNorDb_returnsEmpty() {
        GitHubAppConfigRepository repository = mock(GitHubAppConfigRepository.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        when(repository.findAll()).thenReturn(java.util.List.of());

        GitHubAppConfigServiceImpl service = new GitHubAppConfigServiceImpl(
                repository, encryptionService, RestClient.builder(), "", "", "");

        assertThat(service.resolve()).isEmpty();
    }

    @Test
    void store_encryptsAndUpsertsSingleRow() {
        GitHubAppConfigRepository repository = mock(GitHubAppConfigRepository.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        when(repository.findAll()).thenReturn(java.util.List.of());
        when(encryptionService.encrypt(any())).thenReturn("cipher");

        GitHubAppConfigServiceImpl service = new GitHubAppConfigServiceImpl(
                repository, encryptionService, RestClient.builder(), "", "", "");

        service.store("789", "my-app", "filip", TEST_PEM, "wh-secret");

        org.mockito.ArgumentCaptor<GitHubAppConfig> captor = org.mockito.ArgumentCaptor.forClass(GitHubAppConfig.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        GitHubAppConfig saved = captor.getValue();
        assertThat(saved.getAppId()).isEqualTo("789");
        assertThat(saved.getAppSlug()).isEqualTo("my-app");
        assertThat(saved.getOwnerLogin()).isEqualTo("filip");
        assertThat(saved.getPrivateKeyPemEnc()).isEqualTo("cipher");
        assertThat(saved.getWebhookSecretEnc()).isEqualTo("cipher");
    }
}
