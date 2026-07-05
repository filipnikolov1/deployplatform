package dev.filipnikolov.vector.docker.service.impl;

import dev.filipnikolov.vector.config.DomainConfig;
import dev.filipnikolov.vector.docker.service.DockerService;
import dev.filipnikolov.vector.progress.ProgressFrame;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class DockerServiceImpl implements DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerServiceImpl.class);

    private final DockerClient dockerClient;
    private final String traefikNetwork;
    private final DomainConfig domainConfig;
    private final AuthConfig authConfig;

    public DockerServiceImpl(
            DockerClient dockerClient,
            @Value("${dockerhub.username}") String dockerhubUsername,
            @Value("${dockerhub.token}") String dockerhubToken,
            @Value("${traefik.network}") String traefikNetwork,
            DomainConfig domainConfig) {

        this.dockerClient = dockerClient;
        this.traefikNetwork = traefikNetwork;
        this.domainConfig = domainConfig;

        if (!dockerhubUsername.isEmpty() && !dockerhubToken.isEmpty()) {
            this.authConfig = new AuthConfig()
                    .withUsername(dockerhubUsername)
                    .withPassword(dockerhubToken)
                    .withRegistryAddress("https://index.docker.io/v1/");
        } else {
            this.authConfig = null;
        }
    }

    @Override
    public String pullAndRun(String imageName, String appName, String subdomain, int containerPort, Map<String, String> envVars) throws InterruptedException {
        return pullAndRun(imageName, appName, subdomain, containerPort, envVars, null);
    }

    @Override
    public String pullAndRun(String imageName, String appName, String subdomain, int containerPort, Map<String, String> envVars, Consumer<ProgressFrame> progressCallback) throws InterruptedException {
        Consumer<ProgressFrame> emit = progressCallback != null ? progressCallback : f -> {};
        var pullCmd = dockerClient.pullImageCmd(imageName);
        if (authConfig != null) {
            pullCmd.withAuthConfig(authConfig);
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> pullError = new AtomicReference<>();
        pullCmd.exec(new ResultCallback<PullResponseItem>() {
            @Override
            public void onStart(Closeable closeable) {}

            @Override
            public void onNext(PullResponseItem item) {
                Long current = null;
                Long total = null;
                if (item.getProgressDetail() != null) {
                    current = item.getProgressDetail().getCurrent();
                    total = item.getProgressDetail().getTotal();
                }
                String msg = (item.getStatus() != null ? item.getStatus() : "")
                        + (item.getId() != null ? " " + item.getId() : "");
                emit.accept(new ProgressFrame("PULL_LAYER", msg.strip(), current, total, "bytes", Instant.now()));
            }

            @Override
            public void onError(Throwable throwable) {
                pullError.set(throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void close() {}
        });
        boolean completed = latch.await(10, TimeUnit.MINUTES);
        if (!completed) {
            throw new RuntimeException("Docker pull timed out after 10 minutes for image: " + imageName);
        }

        Throwable err = pullError.get();
        if (err != null) {
            throw new RuntimeException("Docker pull failed for " + imageName + ": " + err.getMessage(), err);
        }

        stopAndRemoveContainer(appName);

        String effectiveHost = (subdomain == null || subdomain.isBlank()) ? appName : subdomain;

        List<String> env = envVars.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();

        emit.accept(new ProgressFrame("CONTAINER_CREATE", "Creating container " + appName, null, null, null, Instant.now()));

        String containerId = dockerClient.createContainerCmd(imageName)
                .withName(appName)
                .withEnv(env)
                .withLabels(Map.of(
                        "traefik.enable", "true",
                        "traefik.http.routers." + appName + ".rule", "Host(`" + domainConfig.appHost(effectiveHost) + "`)",
                        "traefik.http.routers." + appName + ".entrypoints", "web",
                        "traefik.http.services." + appName + ".loadbalancer.server.port", String.valueOf(containerPort)
                ))
                .withHostConfig(HostConfig.newHostConfig()
                        .withNetworkMode(traefikNetwork))
                .exec()
                .getId();

        emit.accept(new ProgressFrame("CONTAINER_START", "Starting container " + appName, null, null, null, Instant.now()));

        dockerClient.startContainerCmd(containerId).exec();
        return containerId;
    }

    @Override
    public boolean imageExistsLocally(String imageName) {
        if (imageName == null || imageName.isBlank()) return false;
        try {
            dockerClient.inspectImageCmd(imageName).exec();
            return true;
        } catch (Exception e) {
            return false;
        }
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
        } catch (NotFoundException ignored) {
            // Container doesn't exist — fine, nothing to stop.
        } catch (Exception e) {
            log.warn("Docker stop failed for {} (continuing to remove): {}", containerName, e.getMessage());
        }
        try {
            dockerClient.removeContainerCmd(containerName).exec();
        } catch (NotFoundException ignored) {
            // Container doesn't exist — fine, nothing to remove.
        } catch (Exception e) {
            log.warn("Docker remove failed for {}: {}", containerName, e.getMessage());
        }
    }

    @Override
    public List<String> getContainerLogs(String containerName, int tailLines) {
        List<String> lines = new ArrayList<>();
        try {
            dockerClient.logContainerCmd(containerName)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(tailLines)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            String line = new String(frame.getPayload(), StandardCharsets.UTF_8)
                                    .stripTrailing();
                            if (!line.isEmpty()) {
                                lines.add(line);
                            }
                        }
                    }).awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Container doesn't exist, never started, or docker socket unavailable —
            // return whatever we've accumulated (likely empty).
        }
        return lines;
    }

    @Override
    public Closeable streamContainerLogs(
            String containerName,
            int tailLines,
            Consumer<String> onLine,
            Consumer<Throwable> onError,
            Runnable onComplete) {
        return dockerClient.logContainerCmd(containerName)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withTail(tailLines)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        String line = new String(frame.getPayload(), StandardCharsets.UTF_8).stripTrailing();
                        if (!line.isEmpty()) {
                            onLine.accept(line);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        onError.accept(throwable);
                    }

                    @Override
                    public void onComplete() {
                        onComplete.run();
                    }
                });
    }
}
