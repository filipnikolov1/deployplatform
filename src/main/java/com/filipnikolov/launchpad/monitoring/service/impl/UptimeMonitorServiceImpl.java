package com.filipnikolov.launchpad.monitoring.service.impl;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.model.DeploymentStatus;
import com.filipnikolov.launchpad.deployment.repository.DeploymentRepository;
import com.filipnikolov.launchpad.monitoring.service.NotificationService;
import com.filipnikolov.launchpad.monitoring.service.UptimeMonitorService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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

    @Value("${traefik.domain}")
    private String traefikDomain;

    private final RestClient restClient = RestClient.create();

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
     * Pings the app's Traefik URL and returns true if it responds with any 2xx status.
     */
    private boolean isHealthy(String appName) {
        try {
            restClient.get()
                    .uri("http://" + appName + "." + traefikDomain)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
