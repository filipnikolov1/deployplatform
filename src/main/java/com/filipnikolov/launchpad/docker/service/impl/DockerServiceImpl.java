package com.filipnikolov.launchpad.docker.service.impl;

import com.filipnikolov.launchpad.docker.buildlog.service.BuildLogService;
import com.filipnikolov.launchpad.docker.service.DockerService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Service
public class DockerServiceImpl implements DockerService {

    private final DockerClient dockerClient;
    private final BuildLogService buildLogService;
    private final String traefikNetwork;
    private final String traefikDomain;
    private final AuthConfig authConfig;

    public DockerServiceImpl(
            BuildLogService buildLogService,
            @Value("${docker.socket}") String dockerSocket,
            @Value("${dockerhub.username}") String dockerhubUsername,
            @Value("${dockerhub.token}") String dockerhubToken,
            @Value("${traefik.network}") String traefikNetwork,
            @Value("${traefik.domain}") String traefikDomain) {

        this.buildLogService = buildLogService;
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

    @Override
    public String pullAndRun(String imageName, String appName, int containerPort, Map<String, String> envVars) throws InterruptedException {
        buildLogService.send(appName, "Pulling image: " + imageName);

        var pullCmd = dockerClient.pullImageCmd(imageName);
        if (authConfig != null) {
            pullCmd.withAuthConfig(authConfig);
        }

        CountDownLatch latch = new CountDownLatch(1);
        pullCmd.exec(new ResultCallback<PullResponseItem>() {
            @Override
            public void onStart(Closeable closeable) {}

            @Override
            public void onNext(PullResponseItem item) {
                String status = item.getStatus();
                if (status != null) {
                    String progress = item.getProgress() != null ? " " + item.getProgress() : "";
                    buildLogService.send(appName, status + progress);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                buildLogService.send(appName, "Pull failed: " + throwable.getMessage());
                latch.countDown();
            }

            @Override
            public void onComplete() {
                buildLogService.send(appName, "Pull complete");
                latch.countDown();
            }

            @Override
            public void close() {}
        });
        latch.await();

        buildLogService.send(appName, "Stopping existing container...");
        stopAndRemoveContainer(appName);

        List<String> env = envVars.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();

        String containerId = dockerClient.createContainerCmd(imageName)
                .withName(appName)
                .withEnv(env)
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

        buildLogService.send(appName, "Starting container: " + containerId.substring(0, 12));
        dockerClient.startContainerCmd(containerId).exec();

        buildLogService.send(appName, "Container running at " + appName + "." + traefikDomain);
        buildLogService.complete(appName);

        return containerId;
    }

    @Override
    public boolean isContainerRunning(String containerName) {
        try {
            return Boolean.TRUE.equals(
                    dockerClient.inspectContainerCmd(containerName).exec().getState().getRunning()
            );
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void stopAndRemoveContainer(String containerName) {
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
