package dev.filipnikolov.vector.connect.service;

import dev.filipnikolov.vector.ai.AiProvider;
import dev.filipnikolov.vector.connect.detect.AiModuleSuggester;
import dev.filipnikolov.vector.connect.detect.DbDetector;
import dev.filipnikolov.vector.connect.detect.ModuleCandidate;
import dev.filipnikolov.vector.connect.detect.ModuleDetector;
import dev.filipnikolov.vector.connect.dto.ConnectRequest;
import dev.filipnikolov.vector.connect.dto.ConnectResponse;
import dev.filipnikolov.vector.connect.dto.ScanResponse;
import dev.filipnikolov.vector.connect.workflow.ModuleJob;
import dev.filipnikolov.vector.connect.workflow.WiringResult;
import dev.filipnikolov.vector.connect.workflow.WorkflowWiringService;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.envvar.service.EnvVarService;
import dev.filipnikolov.vector.events.DeploySource;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.DeploymentStatus;
import dev.filipnikolov.vector.github.client.GitHubAutomationClient;
import dev.filipnikolov.vector.github.client.GitHubException;
import dev.filipnikolov.vector.github.client.dto.WorkflowRun;
import dev.filipnikolov.vector.github.scan.RepoScan;
import dev.filipnikolov.vector.github.scan.RepoScanner;
import dev.filipnikolov.vector.githubapp.RepoUrlNormalizer;
import dev.filipnikolov.vector.githubapp.model.GitHubInstallation;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import dev.filipnikolov.vector.githubapp.model.InstallationStatus;
import dev.filipnikolov.vector.githubapp.model.WorkflowMode;
import dev.filipnikolov.vector.githubapp.repository.GitHubInstallationRepository;
import dev.filipnikolov.vector.githubapp.repository.GitHubRepoRepository;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import dev.filipnikolov.vector.githubapp.service.GitHubAutomationService;
import dev.filipnikolov.vector.project.model.Project;
import dev.filipnikolov.vector.project.model.ProjectEnvVar;
import dev.filipnikolov.vector.project.model.ProjectService;
import dev.filipnikolov.vector.project.repository.ProjectEnvVarRepository;
import dev.filipnikolov.vector.project.repository.ProjectRepository;
import dev.filipnikolov.vector.project.repository.ProjectServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ConnectService {

    private static final Pattern VALID_APP_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$");

    private final GitHubRepoRepository repoRepository;
    private final GitHubInstallationRepository installationRepository;
    private final GitHubAppConfigService configService;
    private final ProjectRepository projectRepository;
    private final ProjectServiceRepository projectServiceRepository;
    private final ProjectEnvVarRepository projectEnvVarRepository;
    private final DeploymentRepository deploymentRepository;
    private final EnvVarService envVarService;
    private final DeploymentEventService deploymentEventService;
    private final WorkflowWiringService workflowWiringService;
    private final AiProvider aiProvider;
    private final RestClient.Builder restClientBuilder;
    private final GitHubAutomationService automationService;

    public ConnectService(GitHubRepoRepository repoRepository,
                           GitHubInstallationRepository installationRepository,
                           GitHubAppConfigService configService,
                           ProjectRepository projectRepository,
                           ProjectServiceRepository projectServiceRepository,
                           ProjectEnvVarRepository projectEnvVarRepository,
                           DeploymentRepository deploymentRepository,
                           EnvVarService envVarService,
                           DeploymentEventService deploymentEventService,
                           WorkflowWiringService workflowWiringService,
                           AiProvider aiProvider,
                           RestClient.Builder restClientBuilder,
                           GitHubAutomationService automationService) {
        this.repoRepository = repoRepository;
        this.installationRepository = installationRepository;
        this.configService = configService;
        this.projectRepository = projectRepository;
        this.projectServiceRepository = projectServiceRepository;
        this.projectEnvVarRepository = projectEnvVarRepository;
        this.deploymentRepository = deploymentRepository;
        this.envVarService = envVarService;
        this.deploymentEventService = deploymentEventService;
        this.workflowWiringService = workflowWiringService;
        this.aiProvider = aiProvider;
        this.restClientBuilder = restClientBuilder;
        this.automationService = automationService;
    }

    public ScanResponse scan(String repoFullName) {
        GitHubRepo repo = repoRepository.findByFullName(repoFullName)
                .orElseThrow(() -> new IllegalArgumentException("Repo not found: " + repoFullName));

        String[] parts = repoFullName.split("/", 2);
        String owner = parts[0];
        String name = parts.length > 1 ? parts[1] : parts[0];
        String ref = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "HEAD";

        RepoScanner scanner = new RepoScanner(
                configService.authProvider().tokenSupplier(repo.getInstallationId()), restClientBuilder);
        RepoScan scan;
        try {
            scan = scanner.scan(owner, name, ref);
        } catch (GitHubException e) {
            throw new IllegalStateException("Failed to scan repo " + repoFullName + ": " + e.getMessage(), e);
        }

        List<ModuleCandidate> deterministic = new ModuleDetector().detect(scan);
        List<ModuleCandidate> candidates = new AiModuleSuggester(aiProvider).refine(scan, deterministic);
        var db = new DbDetector().suggest(scan);

        boolean monorepo = candidates.size() > 1;
        List<ScanResponse.ScannedModule> modules = new ArrayList<>();
        for (ModuleCandidate candidate : candidates) {
            String suggestedAppName = suggestAppName(name, candidate.path(), monorepo);
            modules.add(new ScanResponse.ScannedModule(candidate, suggestedAppName, null));
        }

        return new ScanResponse(modules, db, scan.truncated(), true);
    }

    @Transactional
    public ConnectResponse connect(ConnectRequest req) {
        if (req.modules() == null || req.modules().isEmpty()) {
            throw new IllegalArgumentException("At least one module is required");
        }

        GitHubRepo repo = repoRepository.findByFullName(req.repoFullName())
                .orElseThrow(() -> new IllegalArgumentException("Repo not found: " + req.repoFullName()));

        GitHubInstallation installation = installationRepository.findByInstallationId(repo.getInstallationId())
                .orElseThrow(() -> new InstallationNotApprovedException(
                        "Installation not found for repo: " + req.repoFullName()));
        if (installation.getStatus() != InstallationStatus.APPROVED) {
            throw new InstallationNotApprovedException(
                    "Installation for repo " + req.repoFullName() + " is not approved");
        }

        String owner = req.repoFullName().split("/", 2)[0];
        for (ConnectRequest.ModuleSelection module : req.modules()) {
            assertAppNameFree(module.name());
        }

        List<ModuleJob> jobs = new ArrayList<>();
        List<String> appNames = new ArrayList<>();
        Long projectId = null;

        if (req.modules().size() == 1) {
            ConnectRequest.ModuleSelection module = req.modules().get(0);
            registerApp(module, req, owner, null);
            appNames.add(module.name());
            jobs.add(toModuleJob(module, owner));
        } else {
            Project project = new Project();
            project.setName(projectName(req.repoFullName()));
            project.setRepoFullName(req.repoFullName());
            project.setInstallationId(repo.getInstallationId());
            project.setDefaultBranch(req.branch());
            project.setCreatedAt(LocalDateTime.now());
            project.setUpdatedAt(LocalDateTime.now());
            project = projectRepository.save(project);
            projectId = project.getId();

            for (ConnectRequest.ModuleSelection module : req.modules()) {
                registerApp(module, req, owner, projectId);
                appNames.add(module.name());
                jobs.add(toModuleJob(module, owner));
            }
        }

        deploymentEventService.record(DeploymentEventType.REPO_CONNECTED, DeploymentEventStatus.SUCCESS,
                req.repoFullName(), null, null, null);

        WiringResult wiring = workflowWiringService.wire(repo, req, jobs);

        return new ConnectResponse(projectId, appNames, wiring);
    }

    public List<WorkflowRun> ciStatus(String appName) {
        GitHubRepo repo = resolveRepoForApp(appName);
        String[] parts = repo.getFullName().split("/", 2);
        GitHubAutomationClient client = automationService.forInstallation(repo.getInstallationId());
        return client.recentRuns(parts[0], parts[1], "vector-deploy.yml");
    }

    @Transactional
    public WiringResult switchWorkflowMode(String appName, WorkflowMode mode) {
        GitHubRepo repo = resolveRepoForApp(appName);
        Deployment deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName).orElse(null);
        String branch = deployment != null ? deployment.getBranch() : repo.getDefaultBranch();

        ConnectRequest req = new ConnectRequest(repo.getFullName(), branch, List.of(), mode, false);
        List<ModuleJob> jobs = List.of(new ModuleJob(appName, modulePathForApp(appName), buildModeForApp(appName),
                imageTargetForApp(repo, appName)));

        return workflowWiringService.wire(repo, req, jobs);
    }

    @Transactional
    public void disconnect(String appName) {
        Deployment deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseThrow(() -> new AppNotFoundException("App not found: " + appName));

        deployment.setRepoUrl(null);
        deploymentRepository.save(deployment);

        deploymentEventService.record(DeploymentEventType.REPO_CONNECTED, DeploymentEventStatus.SUCCESS,
                appName, null, null, "disconnected");
    }

    public void redeploy(String appName) {
        GitHubRepo repo = resolveRepoForApp(appName);
        if (repo.getWorkflowMode() != WorkflowMode.MANAGED) {
            throw new CustomWorkflowRedeployException("Redeploy is only available for managed workflows: " + appName);
        }
        Deployment deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName).orElse(null);
        String branch = deployment != null ? deployment.getBranch() : repo.getDefaultBranch();

        String[] parts = repo.getFullName().split("/", 2);
        GitHubAutomationClient client = automationService.forInstallation(repo.getInstallationId());
        client.dispatchWorkflow(parts[0], parts[1], "vector-deploy.yml", branch);

        deploymentEventService.record(DeploymentEventType.REPO_CONNECTED, DeploymentEventStatus.SUCCESS,
                appName, null, null, "dispatched");
    }

    private GitHubRepo resolveRepoForApp(String appName) {
        Optional<Deployment> deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName);
        if (deployment.isPresent()) {
            String fullName = RepoUrlNormalizer.normalize(deployment.get().getRepoUrl());
            return repoRepository.findByFullName(fullName)
                    .orElseThrow(() -> new AppNotFoundException("Repo not found for app: " + appName));
        }

        ProjectService service = projectServiceRepository.findByAppName(appName)
                .orElseThrow(() -> new AppNotFoundException("App not found: " + appName));
        Project project = projectRepository.findById(service.getProjectId())
                .orElseThrow(() -> new AppNotFoundException("Project not found for app: " + appName));
        return repoRepository.findByFullName(project.getRepoFullName())
                .orElseThrow(() -> new AppNotFoundException("Repo not found for app: " + appName));
    }

    private String modulePathForApp(String appName) {
        return projectServiceRepository.findByAppName(appName)
                .map(ProjectService::getModulePath)
                .orElse("");
    }

    private dev.filipnikolov.vector.connect.detect.BuildMode buildModeForApp(String appName) {
        return projectServiceRepository.findByAppName(appName)
                .map(service -> dev.filipnikolov.vector.connect.detect.BuildMode.valueOf(service.getBuildMode()))
                .orElse(dev.filipnikolov.vector.connect.detect.BuildMode.BUILDPACK);
    }

    private String imageTargetForApp(GitHubRepo repo, String appName) {
        String owner = repo.getFullName().split("/", 2)[0];
        return "ghcr.io/" + owner.toLowerCase(Locale.ROOT) + "/" + appName.toLowerCase(Locale.ROOT);
    }

    private void registerApp(ConnectRequest.ModuleSelection module, ConnectRequest req, String owner, Long projectId) {
        Deployment deployment = new Deployment();
        deployment.setAppName(module.name());
        deployment.setRepoUrl("https://github.com/" + req.repoFullName());
        deployment.setBranch(req.branch());
        deployment.setContainerPort(module.port() != null ? module.port() : 8080);
        deployment.setStatus(DeploymentStatus.PROVISIONING);
        deployment.setDeploySource(DeploySource.CONNECTED_REPO);
        deployment.setCreatedAt(LocalDateTime.now());
        deployment.setUpdatedAt(LocalDateTime.now());
        if (module.exposed()) {
            deployment.setSubdomain(module.subdomain());
        }
        deploymentRepository.save(deployment);

        if (module.env() != null) {
            for (Map.Entry<String, String> entry : module.env().entrySet()) {
                envVarService.setEnvVar(module.name(), entry.getKey(), entry.getValue());
            }
        }

        if (projectId != null) {
            dev.filipnikolov.vector.project.model.ProjectService service = new dev.filipnikolov.vector.project.model.ProjectService();
            service.setProjectId(projectId);
            service.setName(module.name());
            service.setModulePath(module.path());
            service.setStack(module.stack());
            service.setBuildMode(module.buildMode().name());
            service.setExposed(module.exposed());
            service.setAppName(module.name());
            service.setCreatedAt(LocalDateTime.now());
            projectServiceRepository.save(service);
        }

        deploymentEventService.record(DeploymentEventType.REPO_CONNECTED, DeploymentEventStatus.SUCCESS,
                module.name(), null, null, null);
    }

    private ModuleJob toModuleJob(ConnectRequest.ModuleSelection module, String owner) {
        String imageTarget = "ghcr.io/" + owner.toLowerCase(Locale.ROOT) + "/" + module.name().toLowerCase(Locale.ROOT);
        return new ModuleJob(module.name(), module.path(), module.buildMode(), imageTarget);
    }

    private void assertAppNameFree(String appName) {
        if (!VALID_APP_NAME.matcher(appName).matches()) {
            throw new IllegalArgumentException("Invalid app name: " + appName);
        }
        if (deploymentRepository.findByAppName(appName).isPresent()) {
            throw new AppNameTakenException("App name already in use: " + appName);
        }
        if (projectServiceRepository.findByAppName(appName).isPresent()) {
            throw new AppNameTakenException("App name already in use: " + appName);
        }
    }

    private String suggestAppName(String repoName, String modulePath, boolean monorepo) {
        String candidate;
        if (!monorepo || modulePath.isEmpty()) {
            candidate = repoName;
        } else {
            String lastSegment = modulePath.contains("/")
                    ? modulePath.substring(modulePath.lastIndexOf('/') + 1)
                    : modulePath;
            candidate = repoName + "-" + lastSegment;
        }
        return sanitizeAppName(candidate);
    }

    private String sanitizeAppName(String candidate) {
        String sanitized = candidate.replaceAll("[^a-zA-Z0-9._-]", "-");
        if (sanitized.isEmpty() || !Character.isLetterOrDigit(sanitized.charAt(0))) {
            sanitized = "app-" + sanitized;
        }
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        return sanitized;
    }

    private String projectName(String repoFullName) {
        int idx = repoFullName.indexOf('/');
        return idx == -1 ? repoFullName : repoFullName.substring(idx + 1);
    }
}
