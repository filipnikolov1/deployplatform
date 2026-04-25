package dev.filipnikolov.vector.docker.service;

import dev.filipnikolov.vector.docker.dto.ContainerStats;

public interface ContainerStatsService {

    /**
     * Returns container stats for the given app. Null-safe — returns zeros if the
     * container is missing or stats cannot be fetched.
     */
    ContainerStats get(String appName);
}
