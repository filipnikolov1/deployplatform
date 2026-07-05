package dev.filipnikolov.vector.githubapp.controller;

import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.githubapp.RepoUrlNormalizer;
import dev.filipnikolov.vector.githubapp.model.GitHubInstallation;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.model.InstallationStatus;
import dev.filipnikolov.vector.githubapp.repository.GitHubInstallationRepository;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import dev.filipnikolov.vector.githubapp.service.InstallationSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/github")
public class GitHubInstallationController {

    private final GitHubInstallationRepository installationRepository;
    private final GitHubRepoRepository repoRepository;
    private final GitHubAppConfigService configService;
    private final InstallationSyncService installationSyncService;
    private final DeploymentEventService deploymentEventService;
    private final DeploymentRepository deploymentRepository;

    public GitHubInstallationController(GitHubInstallationRepository installationRepository,
                                         GitHubRepoRepository repoRepository,
                                         GitHubAppConfigService configService,
                                         InstallationSyncService installationSyncService,
                                         DeploymentEventService deploymentEventService,
                                         DeploymentRepository deploymentRepository) {
        this.installationRepository = installationRepository;
        this.repoRepository = repoRepository;
        this.configService = configService;
        this.installationSyncService = installationSyncService;
        this.deploymentEventService = deploymentEventService;
        this.deploymentRepository = deploymentRepository;
    }

    @GetMapping("/installations")
    public ResponseEntity<?> listInstallations() {
        if (configService.resolve().isEmpty()) {
            return ResponseEntity.status(409).body(Map.of("error", "GitHub App not configured"));
        }

        List<Map<String, Object>> result = installationRepository.findAll().stream()
                .map(installation -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("installationId", installation.getInstallationId());
                    row.put("accountLogin", installation.getAccountLogin());
                    row.put("accountType", installation.getAccountType());
                    row.put("status", installation.getStatus());
                    row.put("suspended", installation.isSuspended());
                    row.put("repoCount", repoRepository.findByInstallationId(installation.getInstallationId()).size());
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/installations/{installationId}/approve")
    public ResponseEntity<?> approveInstallation(@PathVariable Long installationId) {
        GitHubInstallation installation = installationRepository.findByInstallationId(installationId).orElse(null);
        if (installation == null) {
            return ResponseEntity.status(404).body(Map.of("error", "installation not found"));
        }

        installation.setStatus(InstallationStatus.APPROVED);
        installation.setApprovedAt(LocalDateTime.now());
        installationRepository.save(installation);

        deploymentEventService.record(
                DeploymentEventType.INSTALLATION_APPROVED,
                DeploymentEventStatus.SUCCESS,
                installation.getAccountLogin(),
                null, null, null);

        installationSyncService.syncRepos(installationId);

        return ResponseEntity.ok(Map.of("status", "approved"));
    }

    @PostMapping("/installations/{installationId}/reject")
    public ResponseEntity<?> rejectInstallation(@PathVariable Long installationId) {
        GitHubInstallation installation = installationRepository.findByInstallationId(installationId).orElse(null);
        if (installation == null) {
            return ResponseEntity.status(404).body(Map.of("error", "installation not found"));
        }

        installation.setStatus(InstallationStatus.REJECTED);
        installationRepository.save(installation);

        return ResponseEntity.ok(Map.of("status", "rejected"));
    }

    @GetMapping("/repos")
    public ResponseEntity<?> listRepos() {
        if (configService.resolve().isEmpty()) {
            return ResponseEntity.status(409).body(Map.of("error", "GitHub App not configured"));
        }

        Set<String> connectedRepos = deploymentRepository.findAllByDeletedAtIsNull().stream()
                .map(Deployment::getRepoUrl)
                .map(RepoUrlNormalizer::normalize)
                .filter(fullName -> fullName != null)
                .collect(Collectors.toSet());

        List<GitHubInstallation> approved = installationRepository.findByStatus(InstallationStatus.APPROVED);

        List<Map<String, Object>> result = approved.stream()
                .map(installation -> {
                    List<Map<String, Object>> repos = repoRepository.findByInstallationId(installation.getInstallationId()).stream()
                            .map(repo -> {
                                Map<String, Object> repoRow = new LinkedHashMap<>();
                                repoRow.put("fullName", repo.getFullName());
                                repoRow.put("defaultBranch", repo.getDefaultBranch());
                                repoRow.put("private", repo.isPrivate());
                                repoRow.put("badge", connectedRepos.contains(repo.getFullName()) ? "CONNECTED" : "NOT_DEPLOYED");
                                return repoRow;
                            })
                            .collect(Collectors.toList());

                    Map<String, Object> accountRow = new LinkedHashMap<>();
                    accountRow.put("account", installation.getAccountLogin());
                    accountRow.put("repos", repos);
                    return accountRow;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
