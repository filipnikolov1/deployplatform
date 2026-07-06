package dev.filipnikolov.vector.connect.registry;

import com.github.dockerjava.api.model.AuthConfig;
import dev.filipnikolov.vector.github.app.GitHubAppAuthProvider;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistryAuthProviderTest {

    @Test
    void ghcrRefResolvesInstallationTokenAndAttachesAuthConfig() {
        GitHubRepoRepository repoRepository = mock(GitHubRepoRepository.class);
        GitHubAppConfigService configService = mock(GitHubAppConfigService.class);
        GitHubAppAuthProvider authProvider = mock(GitHubAppAuthProvider.class);

        GitHubRepo repo = new GitHubRepo();
        repo.setInstallationId(42L);
        repo.setFullName("acme/widgets");

        when(repoRepository.findByFullName("acme/widgets")).thenReturn(Optional.of(repo));
        when(configService.authProvider()).thenReturn(authProvider);
        when(authProvider.tokenSupplier(42L)).thenReturn(() -> "installation-token");

        RegistryAuthProvider provider = new RegistryAuthProvider(repoRepository, configService);

        Optional<AuthConfig> result = provider.forImage("ghcr.io/acme/widgets:latest");

        assertThat(result).isPresent();
        assertThat(result.get().getPassword()).isEqualTo("installation-token");
        assertThat(result.get().getUsername()).isNotBlank();
        assertThat(result.get().getRegistryAddress()).isEqualTo("ghcr.io");
    }

    @Test
    void dockerhubRefReturnsEmpty() {
        GitHubRepoRepository repoRepository = mock(GitHubRepoRepository.class);
        GitHubAppConfigService configService = mock(GitHubAppConfigService.class);

        RegistryAuthProvider provider = new RegistryAuthProvider(repoRepository, configService);

        Optional<AuthConfig> result = provider.forImage("someuser/someimage:latest");

        assertThat(result).isEmpty();
    }

    @Test
    void unknownGhcrRepoReturnsEmpty() {
        GitHubRepoRepository repoRepository = mock(GitHubRepoRepository.class);
        GitHubAppConfigService configService = mock(GitHubAppConfigService.class);

        when(repoRepository.findByFullName("acme/widgets")).thenReturn(Optional.empty());

        RegistryAuthProvider provider = new RegistryAuthProvider(repoRepository, configService);

        Optional<AuthConfig> result = provider.forImage("ghcr.io/acme/widgets:latest");

        assertThat(result).isEmpty();
    }
}
