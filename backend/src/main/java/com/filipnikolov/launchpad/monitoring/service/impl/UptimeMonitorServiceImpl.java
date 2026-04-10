package com.filipnikolov.launchpad.monitoring.service.impl;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.model.DeploymentStatus;
import com.filipnikolov.launchpad.deployment.repository.DeploymentRepository;
import com.filipnikolov.launchpad.docker.service.DockerService;
import com.filipnikolov.launchpad.monitoring.service.NotificationService;
import com.filipnikolov.launchpad.monitoring.service.UptimeMonitorService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pings each RUNNING app via HTTP every 60 seconds. If an app doesn't respond,
 * marks it as DOWN and sends an email alert via the NotificationService.
 */
@Service
@RequiredArgsConstructor
public class UptimeMonitorServiceImpl implements UptimeMonitorService {

    private static final Logger log = LoggerFactory.getLogger(UptimeMonitorServiceImpl.class);

    private final DeploymentRepository deploymentRepository;
    private final NotificationService notificationService;
    private final DockerService dockerService;

    @Override
    @Scheduled(fixedRate = 60000)
    public void checkAll() {
        // Check RUNNING apps — mark as DOWN if unreachable
        List<Deployment> runningApps = deploymentRepository.findByStatus(DeploymentStatus.RUNNING);
        for (Deployment app : runningApps) {
            if (!isHealthy(app.getAppName())) {
                log.warn("App is down: {}", app.getAppName());
                app.setStatus(DeploymentStatus.DOWN);
                app.setUpdatedAt(LocalDateTime.now());
                deploymentRepository.save(app);
                notificationService.sendDownAlert(app.getAppName());
            }
        }

        // Check DOWN apps — mark as RUNNING if they recovered
        List<Deployment> downApps = deploymentRepository.findByStatus(DeploymentStatus.DOWN);
        for (Deployment app : downApps) {
            if (isHealthy(app.getAppName())) {
                log.info("App recovered: {}", app.getAppName());
                app.setStatus(DeploymentStatus.RUNNING);
                app.setUpdatedAt(LocalDateTime.now());
                deploymentRepository.save(app);
            }
        }
    }

    /**
     * Checks if the app's Docker container is currently running via the Docker API.
     */
    private boolean isHealthy(String appName) {
        return dockerService.isContainerRunning(appName);
    }
}
