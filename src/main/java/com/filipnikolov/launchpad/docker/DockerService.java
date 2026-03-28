package com.filipnikolov.launchpad.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.AuthConfig;
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
    private final AuthConfig authConfig;

    public DockerService(
            @Value("${docker.socket}") String dockerSocket,
            @Value("${dockerhub.username}") String dockerhubUsername,
            @Value("${dockerhub.token}") String dockerhubToken,
            @Value("${traefik.network}") String traefikNetwork,
            @Value("${traefik.domain}") String traefikDomain) {

        this.traefikNetwork = traefikNetwork;
        this.traefikDomain = traefikDomain;

        if (!dockerhubUsername.isEmpty() && !dockerhubToken.isEmpty()) {
            this.authConfig = new AuthConfig()
                    .withUsername(dockerhubUsername)
                    .withPassword(dockerhubToken)
                    .withRegistryAddress("https://index.docker.io/v1/");
        } else {
            this.authConfig = null;
        }

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
        // Pull image from DockerHub (with auth if configured)
        var pullCmd = dockerClient.pullImageCmd(imageName);
        if (authConfig != null) {
            pullCmd.withAuthConfig(authConfig);
        }
        pullCmd.exec(new PullImageResultCallback())
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
