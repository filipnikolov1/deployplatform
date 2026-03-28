package com.filipnikolov.launchpad.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Service
public class DockerService {

    private final DockerClient dockerClient;

    public DockerService() {
        String dockerSocket = "unix:///Users/filip/.orbstack/run/docker.sock";

        DefaultDockerClientConfig config = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withDockerHost(dockerSocket)
                .build();

        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(URI.create(dockerSocket))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    public String pullAndRun(String imageName, String appName, int containerPort, int hostPort) throws InterruptedException {
        // Pull image from DockerHub
        dockerClient.pullImageCmd(imageName)
                .exec(new PullImageResultCallback())
                .awaitCompletion();

        // Stop and remove existing container if it exists
        stopAndRemoveContainer(appName);

        // Configure port binding
        ExposedPort exposed = ExposedPort.tcp(containerPort);
        Ports portBindings = new Ports();
        portBindings.bind(exposed, Ports.Binding.bindPort(hostPort));

        // Create and start the container
        String containerId = dockerClient.createContainerCmd(imageName)
                .withName(appName)
                .withExposedPorts(exposed)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings))
                .exec()
                .getId();

        dockerClient.startContainerCmd(containerId).exec();

        return containerId;
    }

    private void stopAndRemoveContainer(String containerName) {
        try {
            dockerClient.stopContainerCmd(containerName).exec();
            dockerClient.removeContainerCmd(containerName).exec();
        } catch (Exception e) {
            // Container didn't exist, that's fine
        }
    }
}