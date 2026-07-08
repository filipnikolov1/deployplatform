package dev.filipnikolov.vector.docker.service;

import dev.filipnikolov.vector.progress.ProgressFrame;

import java.io.Closeable;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages Docker containers — pulling images, creating/starting containers,
 * and tearing down existing ones. All containers are attached to the Traefik
 * network and labeled for automatic subdomain routing.
 */
public interface DockerService {

    /**
     * Pulls a Docker image (with DockerHub auth if configured), removes any existing
     * container with the same name, and starts a new one with Traefik labels and env vars.
     *
     * @param imageName     the Docker image to pull (e.g. "filipnikolov/myapp:latest")
     * @param appName       the container name and internal Traefik router/service identifier
     * @param subdomain     the Traefik Host() label value; if null or blank, falls back to appName
     * @param containerPort the port the app listens on inside the container
     * @param envVars       environment variables to inject into the container
     * @return the container ID of the newly started container
     * @throws InterruptedException if the pull operation is interrupted
     */
    String pullAndRun(String imageName, String appName, String subdomain, int containerPort, Map<String, String> envVars) throws InterruptedException;

    String pullAndRun(String imageName, String appName, String subdomain, int containerPort, Map<String, String> envVars, Consumer<ProgressFrame> progressCallback) throws InterruptedException;

    /**
     * Same as the 5-arg overload, with {@code exposed} explicitly controlling whether Traefik
     * router labels + port binding are attached (false = background worker: no subdomain/route).
     */
    String pullAndRun(String imageName, String appName, String subdomain, int containerPort, Map<String, String> envVars, boolean exposed) throws InterruptedException;

    String pullAndRun(String imageName, String appName, String subdomain, int containerPort, Map<String, String> envVars, boolean exposed, Consumer<ProgressFrame> progressCallback) throws InterruptedException;

    /**
     * Pulls a Docker image (with GHCR/DockerHub auth if configured) without creating or starting
     * a container. Used for the C14 build-event pipeline's background prefetch, so the image is
     * already local by the time the run-completed swap runs.
     *
     * @param imageRef the Docker image to pull (e.g. "ghcr.io/alice/shop:abc123")
     * @throws InterruptedException if the pull operation is interrupted
     */
    void pullImage(String imageRef) throws InterruptedException;

    /**
     * Stops and removes a container by name. Silently ignores if the container
     * doesn't exist or is already stopped.
     *
     * @param containerName the name of the container to remove
     */
    void stopAndRemoveContainer(String containerName);

    /**
     * Checks if a container with the given name exists and is currently running.
     *
     * @param containerName the name of the container to inspect
     * @return true if the container is in a running state, false otherwise
     */
    boolean isContainerRunning(String containerName);

    /**
     * Returns true if the given image (e.g. "repo/app:git-abc123") is present in the
     * local Docker image cache, false otherwise. Used to mark rollback targets that
     * would require a re-pull vs. those that can roll back instantly.
     */
    boolean imageExistsLocally(String imageName);

    /**
     * Fetches the last {@code tailLines} lines of stdout+stderr from a running container.
     * Returns an empty list if the container does not exist, has never started,
     * or the log fetch fails for any reason.
     *
     * @param containerName the container name (same as appName)
     * @param tailLines     maximum number of lines to return from the tail
     * @return list of log lines, oldest first; never null
     */
    java.util.List<String> getContainerLogs(String containerName, int tailLines);

    /**
     * Starts a follow-mode log stream for a container, invoking callbacks for each
     * new line, on error, and on completion. Returns a Closeable that cancels the
     * stream when closed.
     *
     * @param containerName the container name to stream logs from
     * @param tailLines     number of historical lines to emit before live-tailing
     * @param onLine        invoked for every non-empty log line received
     * @param onError       invoked if the stream fails
     * @param onComplete    invoked when the stream ends normally
     * @return a Closeable that cancels the subscription when closed
     */
    Closeable streamContainerLogs(
            String containerName,
            int tailLines,
            Consumer<String> onLine,
            Consumer<Throwable> onError,
            Runnable onComplete);
}
