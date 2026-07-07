package dev.filipnikolov.vector.project.service;

import dev.filipnikolov.vector.config.DomainConfig;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.envvar.crypto.EncryptionService;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.project.model.Project;
import dev.filipnikolov.vector.project.model.ProjectEnvVar;
import dev.filipnikolov.vector.project.model.ProjectService;
import dev.filipnikolov.vector.project.repository.ProjectEnvVarRepository;
import dev.filipnikolov.vector.project.repository.ProjectRepository;
import dev.filipnikolov.vector.project.repository.ProjectServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectEnvService {

    private final ProjectServiceRepository projectServiceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectEnvVarRepository projectEnvVarRepository;
    private final DeploymentRepository deploymentRepository;
    private final EncryptionService encryptionService;
    private final DomainConfig domainConfig;
    private final DeploymentEventService deploymentEventService;

    public Map<String, String> effectiveEnv(String appName, Map<String, String> appVars) {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.put("PORT", String.valueOf(containerPortFor(appName)));

        Optional<ProjectService> self = projectServiceRepository.findByAppName(appName);
        if (self.isPresent()) {
            ProjectService selfService = self.get();
            for (ProjectService sibling : projectServiceRepository.findByProjectId(selfService.getProjectId())) {
                if (sibling.getAppName().equals(appName) || !sibling.isExposed()) {
                    continue;
                }
                merged.put("SERVICE_" + sanitize(sibling.getName()) + "_URL", publicUrlFor(sibling.getAppName()));
            }
            for (ProjectEnvVar var : projectEnvVarRepository.findByProjectId(selfService.getProjectId())) {
                merged.put(var.getEnvKey(), encryptionService.decrypt(var.getEnvValueEnc()));
            }
        }

        merged.putAll(appVars);
        return merged;
    }

    @Transactional
    public void setProjectEnvVar(Long projectId, String key, String value) {
        ProjectEnvVar var = projectEnvVarRepository.findByProjectIdAndEnvKey(projectId, key)
                .orElseGet(() -> {
                    ProjectEnvVar newVar = new ProjectEnvVar();
                    newVar.setProjectId(projectId);
                    newVar.setEnvKey(key);
                    newVar.setCreatedAt(LocalDateTime.now());
                    return newVar;
                });
        var.setEnvValueEnc(encryptionService.encrypt(value));
        projectEnvVarRepository.save(var);

        deploymentEventService.record(DeploymentEventType.PROJECT_ENV_CHANGED, DeploymentEventStatus.SUCCESS,
                projectNameFor(projectId), null, null, null);
    }

    public List<ProjectEnvVarView> listProjectEnvVars(Long projectId) {
        return projectEnvVarRepository.findByProjectId(projectId).stream()
                .map(var -> new ProjectEnvVarView(var.getEnvKey(), encryptionService.decrypt(var.getEnvValueEnc())))
                .toList();
    }

    @Transactional
    public void deleteProjectEnvVar(Long projectId, String key) {
        projectEnvVarRepository.findByProjectIdAndEnvKey(projectId, key)
                .ifPresent(projectEnvVarRepository::delete);

        deploymentEventService.record(DeploymentEventType.PROJECT_ENV_CHANGED, DeploymentEventStatus.SUCCESS,
                projectNameFor(projectId), null, null, null);
    }

    private String projectNameFor(Long projectId) {
        return projectRepository.findById(projectId).map(Project::getName).orElse(String.valueOf(projectId));
    }

    private int containerPortFor(String appName) {
        return deploymentRepository.findByAppName(appName).map(Deployment::getContainerPort).orElse(0);
    }

    private String publicUrlFor(String appName) {
        Deployment deployment = deploymentRepository.findByAppName(appName).orElse(null);
        String effectiveSubdomain = (deployment == null || deployment.getSubdomain() == null
                || deployment.getSubdomain().isBlank()) ? appName : deployment.getSubdomain();
        String scheme = domainConfig.isLocal() ? "http://" : "https://";
        return scheme + domainConfig.appHost(effectiveSubdomain);
    }

    private String sanitize(String name) {
        return name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }
}
