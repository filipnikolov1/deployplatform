package com.filipnikolov.launchpad.docker.service.impl;

import com.filipnikolov.launchpad.docker.dto.ContainerStats;
import com.filipnikolov.launchpad.docker.service.ContainerStatsService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.InvocationBuilder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ContainerStatsServiceImpl implements ContainerStatsService {

    private static final Logger log = LoggerFactory.getLogger(ContainerStatsServiceImpl.class);
    private static final long CACHE_TTL_MS = 5_000;
    private static final ContainerStats ZERO = new ContainerStats(0.0, 0, 0, 0, 0);

    private final DockerClient dockerClient;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(ContainerStats stats, long expiresAt) {}

    @Override
    public ContainerStats get(String appName) {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(appName);
        if (entry != null && entry.expiresAt > now) {
            return entry.stats;
        }
        ContainerStats fresh = fetch(appName);
        cache.put(appName, new CacheEntry(fresh, now + CACHE_TTL_MS));
        return fresh;
    }

    private ContainerStats fetch(String appName) {
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(appName).exec();
            Statistics raw = dockerClient.statsCmd(appName)
                    .exec(new InvocationBuilder.AsyncResultCallback<Statistics>())
                    .awaitResult();

            double cpuPercent = calcCpuPercent(raw);
            long memUsed = safeLong(raw.getMemoryStats() != null ? raw.getMemoryStats().getUsage() : null) / (1024 * 1024);
            long memLimit = safeLong(raw.getMemoryStats() != null ? raw.getMemoryStats().getLimit() : null) / (1024 * 1024);

            long uptime = 0;
            try {
                String startedAt = inspect.getState().getStartedAt();
                if (startedAt != null && !startedAt.isBlank()) {
                    uptime = Duration.between(Instant.parse(startedAt), Instant.now()).toSeconds();
                }
            } catch (Exception ignored) {}

            int restarts = inspect.getRestartCount() != null ? inspect.getRestartCount() : 0;

            return new ContainerStats(cpuPercent, Math.max(0, memUsed), Math.max(0, memLimit), Math.max(0, uptime), restarts);
        } catch (Exception e) {
            log.debug("Stats fetch failed for {}: {}", appName, e.getMessage());
            return ZERO;
        }
    }

    private static long safeLong(Long v) {
        return v == null ? 0L : v;
    }

    private static double calcCpuPercent(Statistics raw) {
        if (raw == null || raw.getCpuStats() == null || raw.getPreCpuStats() == null) return 0.0;
        try {
            Long totalUsage = raw.getCpuStats().getCpuUsage() != null ? raw.getCpuStats().getCpuUsage().getTotalUsage() : null;
            Long preTotalUsage = raw.getPreCpuStats().getCpuUsage() != null ? raw.getPreCpuStats().getCpuUsage().getTotalUsage() : null;
            Long systemUsage = raw.getCpuStats().getSystemCpuUsage();
            Long preSystemUsage = raw.getPreCpuStats().getSystemCpuUsage();
            Long onlineCpus = raw.getCpuStats().getOnlineCpus();

            if (totalUsage == null || preTotalUsage == null || systemUsage == null || preSystemUsage == null) return 0.0;

            double cpuDelta = totalUsage - preTotalUsage;
            double systemDelta = systemUsage - preSystemUsage;
            double cpus = onlineCpus != null && onlineCpus > 0 ? onlineCpus : 1;

            if (systemDelta <= 0 || cpuDelta < 0) return 0.0;
            return (cpuDelta / systemDelta) * cpus * 100.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
