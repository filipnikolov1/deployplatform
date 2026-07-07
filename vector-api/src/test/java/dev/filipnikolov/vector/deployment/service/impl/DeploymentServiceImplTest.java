package dev.filipnikolov.vector.deployment.service.impl;

import dev.filipnikolov.vector.common.lock.ActionLockService;
import dev.filipnikolov.vector.connect.db.AppsPostgresProvisioner;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentEventRepository;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.docker.service.DockerService;
import dev.filipnikolov.vector.envvar.service.EnvVarService;
import dev.filipnikolov.vector.progress.ProgressHub;
import dev.filipnikolov.vector.project.service.ProjectCollapseService;
import dev.filipnikolov.vector.project.service.ProjectEnvService;
import dev.filipnikolov.vector.selfapp.service.SelfAppUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentServiceImplTest {

    private DeploymentRepository deploymentRepository;
    private DeploymentEventRepository eventRepository;
    private DockerService dockerService;
    private EnvVarService envVarService;
    private DeploymentEventService eventService;
    private DeploymentTransactionHelper txHelper;
    private ProgressHub progressHub;
    private SelfAppUpdateService selfAppUpdateService;
    private AppsPostgresProvisioner appsPostgresProvisioner;
    private ProjectCollapseService projectCollapseService;
    private ProjectEnvService projectEnvService;
    private DeploymentServiceImpl service;

    @BeforeEach
    void setUp() {
        deploymentRepository = mock(DeploymentRepository.class);
        eventRepository = mock(DeploymentEventRepository.class);
        dockerService = mock(DockerService.class);
        envVarService = mock(EnvVarService.class);
        eventService = mock(DeploymentEventService.class);
        txHelper = mock(DeploymentTransactionHelper.class);
        progressHub = mock(ProgressHub.class);
        selfAppUpdateService = mock(SelfAppUpdateService.class);
        appsPostgresProvisioner = mock(AppsPostgresProvisioner.class);
        projectCollapseService = mock(ProjectCollapseService.class);
        projectEnvService = mock(ProjectEnvService.class);

        service = new DeploymentServiceImpl(deploymentRepository, eventRepository, dockerService,
                envVarService, eventService, txHelper, progressHub, selfAppUpdateService,
                appsPostgresProvisioner, projectCollapseService, projectEnvService);
    }

    @Test
    void createDeploymentUsesEffectiveEnv() throws InterruptedException {
        Deployment deployment = new Deployment();
        deployment.setAppName("shop-web");
        deployment.setSubdomain("shop");
        deployment.setContainerPort(3000);
        when(txHelper.preCreate(any())).thenReturn(
                new DeploymentTransactionHelper.LifecycleStart(deployment, "op-1"));
        when(envVarService.getEnvVars("shop-web")).thenReturn(Map.of("FOO", "bar"));
        when(projectEnvService.effectiveEnv("shop-web", Map.of("FOO", "bar")))
                .thenReturn(Map.of("FOO", "bar", "PORT", "3000"));
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("shop-web"))
                .thenReturn(java.util.Optional.of(deployment));

        dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest req =
                new dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest(
                        "shop-web", null, "img", 3000, null, null, null, null, null, "shop",
                        dev.filipnikolov.vector.events.TriggerSource.MANUAL);

        service.createDeployment(req);

        verify(dockerService).pullAndRun(eq("img"), eq("shop-web"), eq("shop"), eq(3000),
                eq(Map.of("FOO", "bar", "PORT", "3000")), any());
    }

    @Test
    void restartDeploymentUsesEffectiveEnv() throws InterruptedException {
        Deployment deployment = new Deployment();
        deployment.setAppName("shop-web");
        deployment.setSubdomain("shop");
        deployment.setContainerPort(3000);
        deployment.setImageName("img");
        when(txHelper.preRestart("shop-web")).thenReturn(
                new DeploymentTransactionHelper.LifecycleStart(deployment, "op-2"));
        when(envVarService.getEnvVars("shop-web")).thenReturn(Map.of("FOO", "bar"));
        when(projectEnvService.effectiveEnv("shop-web", Map.of("FOO", "bar")))
                .thenReturn(Map.of("FOO", "bar", "PORT", "3000"));
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("shop-web"))
                .thenReturn(java.util.Optional.of(deployment));

        service.restartDeployment("shop-web");

        verify(dockerService).pullAndRun(eq("img"), eq("shop-web"), eq("shop"), eq(3000),
                eq(Map.of("FOO", "bar", "PORT", "3000")), any());
    }

    @Test
    void hardDeleteExpiredOrphansDatabaseAndCollapsesProject() {
        Deployment d = new Deployment();
        d.setAppName("shop-api");
        d.setDeletedAt(LocalDateTime.now().minusMinutes(10));
        when(deploymentRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(any()))
                .thenReturn(List.of(d));

        service.hardDeleteExpired();

        verify(appsPostgresProvisioner).orphan("shop-api");
        verify(projectCollapseService).collapse("shop-api");
        verify(deploymentRepository).delete(d);
    }
}
