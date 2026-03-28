package com.filipnikolov.launchpad.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;

@Service
public class DockerService {

    private final DockerClient dockerClient;
    private final String traefikNetwork;
    private final String traefikDomain;

    public DockerService(
            @Value("${docker.socket}") String dockerSocket,
            @Value("${traefik.network}") String traefikNetwork,
            @Value("${traefik.domain}") String traefikDomain) {

        this.traefikNetwork = traefikNetwork;
        this.traefikDomain = traefikDomain;

        DefaultDockerClientConfig config = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withDockerHost(dockerSocket)
                .build();

        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(URI.create(dockerSocket))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    public String pullAndRun(String imageName, String appName, int containerPort) throws InterruptedException {
        // Pull image from DockerHub
        dockerClient.pullImageCmd(imageName)
                .exec(new PullImageResultCallback())
                .awaitCompletion();

        // Stop and remove existing container if it exists
        stopAndRemoveContainer(appName);

        // Create and start the container with Traefik labels
        String containerId = dockerClient.createContainerCmd(imageName)
                .withName(appName)
                .withLabels(Map.of(
                        "traefik.enable", "true",
                        "traefik.http.routers." + appName + ".rule", "Host(`" + appName + "." + traefikDomain + "`)",
                        "traefik.http.routers." + appName + ".entrypoints", "web",
                        "traefik.http.services." + appName + ".loadbalancer.server.port", String.valueOf(containerPort)
                ))
                .withHostConfig(HostConfig.newHostConfig()
                        .withNetworkMode(traefikNetwork))
                .exec()
                .getId();

        dockerClient.startContainerCmd(containerId).exec();

        return containerId;
    }

    private void stopAndRemoveContainer(String containerName) {
        try {
            dockerClient.stopContainerCmd(containerName).exec();
        } catch (Exception e) {
            // Container not running or doesn't exist
        }
        try {
            dockerClient.removeContainerCmd(containerName).exec();
        } catch (Exception e) {
            // Container doesn't exist
        }
    }
}
