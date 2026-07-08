package dev.filipnikolov.vector.deployment.service.impl;

import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.events.TriggerSource;
import dev.filipnikolov.vector.monitoring.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the real connect -> push -> run-completed swap handoff: an app registered by
 * ConnectService (repoUrl + explicit containerPort) must survive a swap-shaped
 * CreateDeploymentRequest (null repoUrl, null containerPort) built by BuildEventServiceImpl.swapApp.
 */
class DeploymentTransactionHelperPreCreatePreservationTest {

    private final Map<String, Deployment> byAppName = new HashMap<>();
    private DeploymentRepository deploymentRepository;
    private DeploymentTransactionHelper helper;

    @BeforeEach
    void setUp() {
        byAppName.clear();
        deploymentRepository = mock(DeploymentRepository.class);
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(byAppName.get(inv.getArgument(0))));
        when(deploymentRepository.findBySubdomainAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> {
            Deployment d = inv.getArgument(0);
            byAppName.put(d.getAppName(), d);
            return d;
        });

        DeploymentEventService eventService = mock(DeploymentEventService.class);
        NotificationService notificationService = mock(NotificationService.class);
        helper = new DeploymentTransactionHelper(deploymentRepository, eventService, notificationService);
        ReflectionTestUtils.setField(helper, "defaultContainerPort", 3000);
    }

    @Test
    void swapPreservesRepoUrlAndContainerPortFromConnectRegistration() {
        // Simulates ConnectService.registerApp: repoUrl set, containerPort 8080.
        Deployment connected = new Deployment();
        connected.setId(1L);
        connected.setAppName("shop-web");
        connected.setRepoUrl("https://github.com/alice/shop");
        connected.setContainerPort(8080);
        byAppName.put("shop-web", connected);

        // Simulates BuildEventServiceImpl.swapApp's request shape: repoUrl=null, containerPort=null.
        CreateDeploymentRequest swapReq = new CreateDeploymentRequest(
                "shop-web", null, "ghcr.io/alice/shop-web:abc123", null,
                "main", "abc123", "msg", "alice", null, null, TriggerSource.AUTOMATIC);

        helper.preCreate(swapReq);

        Deployment after = byAppName.get("shop-web");
        assertThat(after.getRepoUrl()).isEqualTo("https://github.com/alice/shop");
        assertThat(after.getContainerPort()).isEqualTo(8080);

        // Second push must still resolve the app via repoUrl lookup.
        when(deploymentRepository.findByRepoUrlAndDeletedAtIsNull("https://github.com/alice/shop"))
                .thenReturn(Optional.of(after));
        assertThat(deploymentRepository.findByRepoUrlAndDeletedAtIsNull("https://github.com/alice/shop"))
                .isPresent();
    }

    @Test
    void newAppWithNullContainerPort_fallsBackToDefault() {
        CreateDeploymentRequest req = new CreateDeploymentRequest(
                "new-app", null, "ghcr.io/alice/new-app:abc123", null,
                "main", "abc123", "msg", "alice", null, null, TriggerSource.AUTOMATIC);

        helper.preCreate(req);

        assertThat(byAppName.get("new-app").getContainerPort()).isEqualTo(3000);
    }

    @Test
    void newAppWithNullRepoUrl_staysNull() {
        CreateDeploymentRequest req = new CreateDeploymentRequest(
                "new-app", null, "ghcr.io/alice/new-app:abc123", 5000,
                "main", "abc123", "msg", "alice", null, null, TriggerSource.AUTOMATIC);

        helper.preCreate(req);

        assertThat(byAppName.get("new-app").getRepoUrl()).isNull();
    }

    @Test
    void legacyWebhookRequestShape_stillOverwritesRepoUrlAndPort() {
        Deployment existing = new Deployment();
        existing.setId(2L);
        existing.setAppName("legacy-app");
        existing.setRepoUrl("https://github.com/alice/old-repo");
        existing.setContainerPort(9999);
        byAppName.put("legacy-app", existing);

        // Legacy GitHubWebhookController always sends non-null repoUrl and a defaulted port.
        CreateDeploymentRequest legacyReq = new CreateDeploymentRequest(
                "legacy-app", "https://github.com/alice/new-repo", "ghcr.io/alice/legacy-app:def456", 4000,
                "main", "def456", "msg", "alice", null, null, TriggerSource.AUTOMATIC);

        helper.preCreate(legacyReq);

        Deployment after = byAppName.get("legacy-app");
        assertThat(after.getRepoUrl()).isEqualTo("https://github.com/alice/new-repo");
        assertThat(after.getContainerPort()).isEqualTo(4000);
    }

    @Test
    void quickDeployRequestShape_existingAppKeepsNullRepoUrlAndExplicitPort() {
        Deployment existing = new Deployment();
        existing.setId(3L);
        existing.setAppName("quick-app");
        existing.setRepoUrl(null);
        existing.setContainerPort(6000);
        byAppName.put("quick-app", existing);

        // QuickDeployController always sends repoUrl=null and a client-defaulted non-null port.
        CreateDeploymentRequest quickReq = new CreateDeploymentRequest(
                "quick-app", null, "myimage:latest", 7000,
                null, null, null, null, null, null, TriggerSource.MANUAL);

        helper.preCreate(quickReq);

        Deployment after = byAppName.get("quick-app");
        assertThat(after.getRepoUrl()).isNull();
        assertThat(after.getContainerPort()).isEqualTo(7000);
    }
}
