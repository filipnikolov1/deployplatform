package dev.filipnikolov.vector.docker.service.impl;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.PullResponseItem;
import dev.filipnikolov.vector.config.DomainConfig;
import dev.filipnikolov.vector.connect.registry.RegistryAuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DockerServiceImplTest {

    private DockerClient dockerClient;
    private DomainConfig domainConfig;
    private RegistryAuthProvider registryAuthProvider;
    private DockerServiceImpl service;

    @BeforeEach
    void setUp() {
        dockerClient = mock(DockerClient.class);
        domainConfig = mock(DomainConfig.class);
        registryAuthProvider = mock(RegistryAuthProvider.class);

        when(registryAuthProvider.forImage(anyString())).thenReturn(Optional.empty());
        when(domainConfig.appHost(anyString())).thenAnswer(inv -> inv.getArgument(0) + ".example.com");

        service = new DockerServiceImpl(dockerClient, "", "", "traefik-net", domainConfig, registryAuthProvider);

        PullImageCmd pullImageCmd = mock(PullImageCmd.class, Answers.RETURNS_SELF);
        when(dockerClient.pullImageCmd(anyString())).thenReturn(pullImageCmd);
        when(pullImageCmd.exec(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            ResultCallback<PullResponseItem> callback = inv.getArgument(0);
            callback.onComplete();
            return null;
        });

        CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class, Answers.RETURNS_SELF);
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createContainerCmd);
        CreateContainerResponse createResponse = mock(CreateContainerResponse.class);
        when(createResponse.getId()).thenReturn("container-1");
        when(createContainerCmd.exec()).thenReturn(createResponse);

        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class, Answers.RETURNS_SELF);
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);
    }

    @Test
    void pullAndRun_exposedTrue_setsTraefikLabels() throws InterruptedException {
        service.pullAndRun("img", "shop-web", "shop", 3000, Map.of(), true);

        ArgumentCaptor<Map<String, String>> labelsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dockerClient.createContainerCmd("img")).withLabels(labelsCaptor.capture());
        Map<String, String> labels = labelsCaptor.getValue();
        assertThat(labels).containsEntry("traefik.enable", "true");
        assertThat(labels).containsKey("traefik.http.routers.shop-web.rule");
        assertThat(labels).containsKey("traefik.http.services.shop-web.loadbalancer.server.port");
    }

    @Test
    void pullAndRun_exposedFalse_omitsTraefikLabels() throws InterruptedException {
        service.pullAndRun("img", "worker", null, 3000, Map.of(), false);

        ArgumentCaptor<Map<String, String>> labelsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dockerClient.createContainerCmd("img")).withLabels(labelsCaptor.capture());
        Map<String, String> labels = labelsCaptor.getValue();
        assertThat(labels).doesNotContainKey("traefik.enable");
        assertThat(labels).doesNotContainKey("traefik.http.routers.worker.rule");
        assertThat(labels).doesNotContainKey("traefik.http.services.worker.loadbalancer.server.port");
    }

    @Test
    void pullAndRun_defaultOverload_defaultsExposedTrue() throws InterruptedException {
        service.pullAndRun("img", "shop-web", "shop", 3000, Map.of());

        ArgumentCaptor<Map<String, String>> labelsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dockerClient.createContainerCmd("img")).withLabels(labelsCaptor.capture());
        assertThat(labelsCaptor.getValue()).containsEntry("traefik.enable", "true");
    }

    @Test
    void pullImage_pullsWithoutCreatingContainer() throws InterruptedException {
        service.pullImage("img");

        verify(dockerClient).pullImageCmd("img");
        verify(dockerClient, never()).createContainerCmd(anyString());
    }
}
