package dev.filipnikolov.vector.connect.registry;

import com.github.dockerjava.api.model.AuthConfig;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RegistryAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(RegistryAuthProvider.class);
    private static final String GHCR_PREFIX = "ghcr.io/";

    private final GitHubRepoRepository repoRepository;
    private final GitHubAppConfigService configService;

    public RegistryAuthProvider(GitHubRepoRepository repoRepository, GitHubAppConfigService configService) {
        this.repoRepository = repoRepository;
        this.configService = configService;
    }

    public Optional<AuthConfig> forImage(String imageName) {
        if (imageName == null || !imageName.startsWith(GHCR_PREFIX)) {
            return Optional.empty();
        }

        Optional<String> fullName = repoFullName(imageName);
        if (fullName.isEmpty()) {
            log.warn("Malformed GHCR image ref {}; pulling without auth", imageName);
            return Optional.empty();
        }
        Optional<GitHubRepo> repo = repoRepository.findByFullName(fullName.get());
        if (repo.isEmpty()) {
            log.warn("No github_repo found for GHCR image {} (owner/repo {}); pulling without auth", imageName, fullName.get());
            return Optional.empty();
        }

        String token = configService.authProvider().tokenSupplier(repo.get().getInstallationId()).get();
        return Optional.of(new AuthConfig()
                .withUsername("x-access-token")
                .withPassword(token)
                .withRegistryAddress("ghcr.io"));
    }

    private Optional<String> repoFullName(String imageName) {
        String rest = imageName.substring(GHCR_PREFIX.length());
        String withoutTag = rest.split("[:@]")[0];
        String[] segments = withoutTag.split("/");
        if (segments.length < 2) {
            return Optional.empty();
        }
        return Optional.of(segments[0] + "/" + segments[1]);
    }
}
