package dev.filipnikolov.vector.githubapp.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.github.client.GitHubClient;
import dev.filipnikolov.vector.githubapp.dto.InstallationPayload;
import dev.filipnikolov.vector.githubapp.dto.InstallationRepositoriesPayload;
import dev.filipnikolov.vector.githubapp.model.GitHubInstallation;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.model.InstallationStatus;
import dev.filipnikolov.vector.githubapp.repository.GitHubInstallationRepository;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import dev.filipnikolov.vector.githubapp.service.InstallationSyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.function.Function;

@Service
public class InstallationSyncServiceImpl implements InstallationSyncService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PAGE_SIZE = 100;

    private final GitHubInstallationRepository installationRepository;
    private final GitHubRepoRepository repoRepository;
    private final GitHubAppConfigService configService;
    private final DeploymentEventService deploymentEventService;

    private Function<String, GitHubClient> clientFactory = GitHubClient::new;

    public InstallationSyncServiceImpl(GitHubInstallationRepository installationRepository,
                                        GitHubRepoRepository repoRepository,
                                        GitHubAppConfigService configService,
                                        DeploymentEventService deploymentEventService) {
        this.installationRepository = installationRepository;
        this.repoRepository = repoRepository;
        this.configService = configService;
        this.deploymentEventService = deploymentEventService;
    }

    @Override
    @Transactional
    public void handleInstallation(InstallationPayload payload) {
        InstallationPayload.Installation installation = payload.installation();
        GitHubInstallation entity = installationRepository.findByInstallationId(installation.id())
                .orElseGet(() -> newInstallation(installation));

        switch (payload.action()) {
            case "created" -> {
                String ownerLogin = configService.resolve().map(GitHubAppConfigService.AppCredentials::ownerLogin).orElse(null);
                if (ownerLogin != null && ownerLogin.equalsIgnoreCase(installation.account().login())) {
                    entity.setStatus(InstallationStatus.APPROVED);
                    entity.setApprovedAt(LocalDateTime.now());
                } else {
                    entity.setStatus(InstallationStatus.PENDING);
                    deploymentEventService.record(
                            DeploymentEventType.INSTALLATION_PENDING,
                            DeploymentEventStatus.SUCCESS,
                            installation.account().login(),
                            null, null, null);
                }
            }
            case "deleted" -> {
                entity.setStatus(InstallationStatus.REMOVED);
                repoRepository.deleteByInstallationId(installation.id());
            }
            case "suspend" -> entity.setSuspended(true);
            case "unsuspend" -> entity.setSuspended(false);
            default -> {
            }
        }

        installationRepository.save(entity);
    }

    @Override
    public void handleInstallationRepositories(InstallationRepositoriesPayload payload) {
        Long installationId = payload.installation().id();

        if (payload.repositoriesAdded() != null) {
            for (InstallationRepositoriesPayload.RepoInfo repoInfo : payload.repositoriesAdded()) {
                GitHubRepo repo = repoRepository.findByFullName(repoInfo.fullName()).orElseGet(GitHubRepo::new);
                repo.setInstallationId(installationId);
                repo.setFullName(repoInfo.fullName());
                repo.setPrivate(repoInfo.isPrivate());
                if (repoInfo.defaultBranch() != null) {
                    repo.setDefaultBranch(repoInfo.defaultBranch());
                }
                repo.setLastSeenAt(LocalDateTime.now());
                repoRepository.save(repo);
            }
        }

        if (payload.repositoriesRemoved() != null) {
            for (InstallationRepositoriesPayload.RepoInfo repoInfo : payload.repositoriesRemoved()) {
                repoRepository.findByFullName(repoInfo.fullName()).ifPresent(repoRepository::delete);
            }
        }
    }

    @Override
    public void syncRepos(Long installationId) {
        String token = configService.authProvider().tokenFor(installationId);
        GitHubClient client = clientFactory.apply(token);

        int page = 1;
        while (true) {
            String url = "https://api.github.com/installation/repositories?per_page=" + PAGE_SIZE + "&page=" + page;
            String json = client.get(url, String.class);
            if (json == null) {
                break;
            }

            JsonNode repositories;
            try {
                repositories = MAPPER.readTree(json).path("repositories");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse installation repositories response", e);
            }

            if (!repositories.isArray() || repositories.isEmpty()) {
                break;
            }

            for (JsonNode repoNode : repositories) {
                String fullName = repoNode.path("full_name").asText();
                GitHubRepo repo = repoRepository.findByFullName(fullName).orElseGet(GitHubRepo::new);
                repo.setInstallationId(installationId);
                repo.setFullName(fullName);
                repo.setPrivate(repoNode.path("private").asBoolean());
                if (repoNode.hasNonNull("default_branch")) {
                    repo.setDefaultBranch(repoNode.path("default_branch").asText());
                }
                repo.setLastSeenAt(LocalDateTime.now());
                repoRepository.save(repo);
            }

            if (repositories.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
    }

    private static GitHubInstallation newInstallation(InstallationPayload.Installation installation) {
        GitHubInstallation entity = new GitHubInstallation();
        entity.setInstallationId(installation.id());
        entity.setAccountLogin(installation.account().login());
        entity.setAccountType(installation.account().type());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }
}
