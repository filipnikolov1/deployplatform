package dev.filipnikolov.vector.docker.dto;

public record ContainerStats(
        double cpuPercent,
        long memoryUsedMB,
        long memoryLimitMB,
        long uptimeSeconds,
        int restartCount
) {}
