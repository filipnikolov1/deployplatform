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
 * Checks each RUNNING app's container liveness every 60 seconds via the
 * Docker API. If a container is no longer running, marks the app as DOWN
 * and sends an email alert via the NotificationService. This is NOT an
 * HTTP health probe — a crashed-but-not-exited app process will still
 * look healthy here.
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
        List<Deployment> runningApps = deploymentRepository.findByStatusAndDeletedAtIsNull(DeploymentStatus.RUNNING);
        for (Deployment app : runningApps) {
            if (!isContainerRunning(app.getAppName())) {
                log.warn("App is down: {}", app.getAppName());
                app.setStatus(DeploymentStatus.DOWN);
                app.setUpdatedAt(LocalDateTime.now());
                deploymentRepository.save(app);
                notificationService.sendDownAlert(app.getAppName());
            }
        }

        // Check DOWN apps — mark as RUNNING if they recovered
        List<Deployment> downApps = deploymentRepository.findByStatusAndDeletedAtIsNull(DeploymentStatus.DOWN);
        for (Deployment app : downApps) {
            if (isContainerRunning(app.getAppName())) {
                log.info("App recovered: {}", app.getAppName());
                app.setStatus(DeploymentStatus.RUNNING);
                app.setUpdatedAt(LocalDateTime.now());
                deploymentRepository.save(app);
            }
        }
    }

    /**
     * Container-liveness check — asks the Docker API whether the container
     * for this app is currently in a running state. Does not probe HTTP.
     */
    private boolean isContainerRunning(String appName) {
        return dockerService.isContainerRunning(appName);
    }
}
