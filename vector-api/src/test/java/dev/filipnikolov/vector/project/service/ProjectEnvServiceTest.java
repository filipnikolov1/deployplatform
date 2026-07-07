package dev.filipnikolov.vector.project.service;

import dev.filipnikolov.vector.config.DomainConfig;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.envvar.crypto.EncryptionService;
import dev.filipnikolov.vector.project.model.Project;
import dev.filipnikolov.vector.project.model.ProjectEnvVar;
import dev.filipnikolov.vector.project.model.ProjectService;
import dev.filipnikolov.vector.project.repository.ProjectEnvVarRepository;
import dev.filipnikolov.vector.project.repository.ProjectRepository;
import dev.filipnikolov.vector.project.repository.ProjectServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectEnvServiceTest {

    private ProjectServiceRepository projectServiceRepository;
    private ProjectRepository projectRepository;
    private ProjectEnvVarRepository projectEnvVarRepository;
    private DeploymentRepository deploymentRepository;
    private EncryptionService encryptionService;
    private DomainConfig domainConfig;
    private DeploymentEventService deploymentEventService;
    private ProjectEnvService service;

    @BeforeEach
    void setUp() {
        projectServiceRepository = mock(ProjectServiceRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectEnvVarRepository = mock(ProjectEnvVarRepository.class);
        deploymentRepository = mock(DeploymentRepository.class);
        encryptionService = mock(EncryptionService.class);
        domainConfig = mock(DomainConfig.class);
        deploymentEventService = mock(DeploymentEventService.class);

        when(encryptionService.encrypt(anyString())).thenAnswer(inv -> "enc(" + inv.getArgument(0) + ")");
        when(encryptionService.decrypt(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s.substring(4, s.length() - 1);
        });
        when(domainConfig.isLocal()).thenReturn(false);
        when(domainConfig.appHost(anyString())).thenAnswer(inv -> inv.getArgument(0) + ".apps.example.com");

        service = new ProjectEnvService(projectServiceRepository, projectRepository, projectEnvVarRepository,
                deploymentRepository, encryptionService, domainConfig, deploymentEventService);
    }

    private ProjectService svc(Long projectId, String name, String appName, boolean exposed) {
        ProjectService s = new ProjectService();
        s.setProjectId(projectId);
        s.setName(name);
        s.setAppName(appName);
        s.setExposed(exposed);
        return s;
    }

    private Deployment deployment(String appName, String subdomain, int containerPort) {
        Deployment d = new Deployment();
        d.setAppName(appName);
        d.setSubdomain(subdomain);
        d.setContainerPort(containerPort);
        return d;
    }

    @Test
    void noProjectRowPassesThroughAppVarsButStillInjectsPort() {
        when(projectServiceRepository.findByAppName("standalone")).thenReturn(Optional.empty());
        when(deploymentRepository.findByAppName("standalone"))
                .thenReturn(Optional.of(deployment("standalone", null, 4000)));

        Map<String, String> result = service.effectiveEnv("standalone", Map.of("FOO", "bar"));

        assertThat(result).containsEntry("FOO", "bar");
        assertThat(result).containsEntry("PORT", "4000");
    }

    @Test
    void twoSiblingsInjectEachOthersUrlBothDirections() {
        ProjectService web = svc(1L, "shop-web", "shop-web", true);
        ProjectService api = svc(1L, "shop-api", "shop-api", true);
        when(projectServiceRepository.findByAppName("shop-web")).thenReturn(Optional.of(web));
        when(projectServiceRepository.findByAppName("shop-api")).thenReturn(Optional.of(api));
        when(projectServiceRepository.findByProjectId(1L)).thenReturn(List.of(web, api));
        when(projectEnvVarRepository.findByProjectIdAndEnvKey(any(), any())).thenReturn(Optional.empty());
        when(projectRepository.findById(1L)).thenReturn(Optional.of(new Project()));
        when(deploymentRepository.findByAppName("shop-web"))
                .thenReturn(Optional.of(deployment("shop-web", null, 3000)));
        when(deploymentRepository.findByAppName("shop-api"))
                .thenReturn(Optional.of(deployment("shop-api", null, 8080)));

        Map<String, String> webEnv = service.effectiveEnv("shop-web", Map.of());
        Map<String, String> apiEnv = service.effectiveEnv("shop-api", Map.of());

        assertThat(webEnv).containsKey("SERVICE_SHOP_API_URL");
        assertThat(apiEnv).containsKey("SERVICE_SHOP_WEB_URL");
    }

    @Test
    void exposedFalseSiblingProducesNoServiceEntry() {
        ProjectService web = svc(1L, "shop-web", "shop-web", true);
        ProjectService worker = svc(1L, "shop-worker", "shop-worker", false);
        when(projectServiceRepository.findByAppName("shop-web")).thenReturn(Optional.of(web));
        when(projectServiceRepository.findByProjectId(1L)).thenReturn(List.of(web, worker));
        when(deploymentRepository.findByAppName("shop-web"))
                .thenReturn(Optional.of(deployment("shop-web", null, 3000)));

        Map<String, String> result = service.effectiveEnv("shop-web", Map.of());

        assertThat(result.keySet()).noneMatch(k -> k.startsWith("SERVICE_SHOP_WORKER"));
    }

    @Test
    void selfExcludedFromInjectedVars() {
        ProjectService web = svc(1L, "shop-web", "shop-web", true);
        when(projectServiceRepository.findByAppName("shop-web")).thenReturn(Optional.of(web));
        when(projectServiceRepository.findByProjectId(1L)).thenReturn(List.of(web));
        when(deploymentRepository.findByAppName("shop-web"))
                .thenReturn(Optional.of(deployment("shop-web", null, 3000)));

        Map<String, String> result = service.effectiveEnv("shop-web", Map.of());

        assertThat(result.keySet()).noneMatch(k -> k.startsWith("SERVICE_SHOP_WEB"));
    }

    @Test
    void sanitizationRuleUppercasesAndReplacesNonAlphanumerics() {
        ProjectService web = svc(1L, "shop-web", "shop-web", true);
        ProjectService api = svc(1L, "shop-api", "shop-api", true);
        when(projectServiceRepository.findByAppName("shop-web")).thenReturn(Optional.of(web));
        when(projectServiceRepository.findByProjectId(1L)).thenReturn(List.of(web, api));
        when(deploymentRepository.findByAppName("shop-web"))
                .thenReturn(Optional.of(deployment("shop-web", null, 3000)));
        when(deploymentRepository.findByAppName("shop-api"))
                .thenReturn(Optional.of(deployment("shop-api", null, 8080)));

        Map<String, String> result = service.effectiveEnv("shop-web", Map.of());

        assertThat(result).containsKey("SERVICE_SHOP_API_URL");
    }

    @Test
    void portInjectedAtLowestPrecedenceUserSetPortWins() {
        when(projectServiceRepository.findByAppName("standalone")).thenReturn(Optional.empty());
        when(deploymentRepository.findByAppName("standalone"))
                .thenReturn(Optional.of(deployment("standalone", null, 4000)));

        Map<String, String> result = service.effectiveEnv("standalone", Map.of("PORT", "9999"));

        assertThat(result).containsEntry("PORT", "9999");
    }

    @Test
    void precedenceAppOverridesProjectOverridesInjected() {
        ProjectService web = svc(1L, "shop-web", "shop-web", true);
        ProjectService api = svc(1L, "shop-api", "shop-api", true);
        when(projectServiceRepository.findByAppName("shop-web")).thenReturn(Optional.of(web));
        when(projectServiceRepository.findByProjectId(1L)).thenReturn(List.of(web, api));
        when(deploymentRepository.findByAppName("shop-web"))
                .thenReturn(Optional.of(deployment("shop-web", null, 3000)));
        when(deploymentRepository.findByAppName("shop-api"))
                .thenReturn(Optional.of(deployment("shop-api", null, 8080)));

        ProjectEnvVar projectVar = new ProjectEnvVar();
        projectVar.setProjectId(1L);
        projectVar.setEnvKey("SERVICE_SHOP_API_URL");
        projectVar.setEnvValueEnc("enc(project-level-value)");
        when(projectEnvVarRepository.findByProjectId(1L)).thenReturn(List.of(projectVar));

        Map<String, String> result = service.effectiveEnv("shop-web",
                Map.of("SERVICE_SHOP_API_URL", "app-level-value"));

        assertThat(result).containsEntry("SERVICE_SHOP_API_URL", "app-level-value");
    }

    @Test
    void urlRecomputedWhenSiblingSubdomainChanges() {
        ProjectService web = svc(1L, "shop-web", "shop-web", true);
        ProjectService api = svc(1L, "shop-api", "shop-api", true);
        when(projectServiceRepository.findByAppName("shop-web")).thenReturn(Optional.of(web));
        when(projectServiceRepository.findByProjectId(1L)).thenReturn(List.of(web, api));
        when(deploymentRepository.findByAppName("shop-web"))
                .thenReturn(Optional.of(deployment("shop-web", null, 3000)));
        when(deploymentRepository.findByAppName("shop-api"))
                .thenReturn(Optional.of(deployment("shop-api", "old-sub", 8080)));

        Map<String, String> first = service.effectiveEnv("shop-web", Map.of());

        when(deploymentRepository.findByAppName("shop-api"))
                .thenReturn(Optional.of(deployment("shop-api", "new-sub", 8080)));

        Map<String, String> second = service.effectiveEnv("shop-web", Map.of());

        assertThat(first.get("SERVICE_SHOP_API_URL")).isNotEqualTo(second.get("SERVICE_SHOP_API_URL"));
    }

    @Test
    void projectEnvCrudRoundTripWithEncryptionAndAudit() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, "shop")));
        when(projectEnvVarRepository.findByProjectIdAndEnvKey(1L, "API_KEY")).thenReturn(Optional.empty());

        service.setProjectEnvVar(1L, "API_KEY", "secret");

        var captor = org.mockito.ArgumentCaptor.forClass(ProjectEnvVar.class);
        verify(projectEnvVarRepository).save(captor.capture());
        assertThat(captor.getValue().getEnvValueEnc()).isEqualTo("enc(secret)");
        verify(deploymentEventService).record(
                org.mockito.ArgumentMatchers.eq(dev.filipnikolov.vector.events.DeploymentEventType.PROJECT_ENV_CHANGED),
                org.mockito.ArgumentMatchers.eq(dev.filipnikolov.vector.events.DeploymentEventStatus.SUCCESS),
                org.mockito.ArgumentMatchers.eq("shop"), any(), any(), any());
    }

    private Project project(Long id, String name) {
        Project p = new Project();
        p.setId(id);
        p.setName(name);
        return p;
    }
}
