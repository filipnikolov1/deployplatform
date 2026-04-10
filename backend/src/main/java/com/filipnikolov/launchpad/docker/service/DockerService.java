package com.filipnikolov.launchpad.docker.service;

import java.util.Map;

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
     * @param appName       the container name and Traefik subdomain identifier
     * @param containerPort the port the app listens on inside the container
     * @param envVars       environment variables to inject into the container
     * @return the container ID of the newly started container
     * @throws InterruptedException if the pull operation is interrupted
     */
    String pullAndRun(String imageName, String appName, int containerPort, Map<String, String> envVars) throws InterruptedException;

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
     * Fetches the last {@code tailLines} lines of stdout+stderr from a running container.
     * Returns an empty list if the container does not exist, has never started,
     * or the log fetch fails for any reason.
     *
     * @param containerName the container name (same as appName)
     * @param tailLines     maximum number of lines to return from the tail
     * @return list of log lines, oldest first; never null
     */
    java.util.List<String> getContainerLogs(String containerName, int tailLines);
}
