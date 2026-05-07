package dev.filipnikolov.vector.deployment.service.impl;

import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.DeploymentStatus;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.exception.ResourceNotFoundException;
import dev.filipnikolov.vector.monitoring.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class DeploymentTransactionHelper {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventService eventService;
    private final NotificationService notificationService;

    @Value("${app.default-port:3000}")
    private int defaultContainerPort;

    @Transactional
    LifecycleStart preCreate(CreateDeploymentRequest req) {
        Deployment deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(req.appName())
                .orElseGet(() -> {
                    Deployment d = new Deployment();
                    d.setAppName(req.appName());
                    d.setCreatedAt(LocalDateTime.now());
                    return d;
                });
        deployment.setRepoUrl(req.repoUrl());
        deployment.setImageName(req.imageName());
        deployment.setContainerPort(req.containerPort() != null ? req.containerPort() : defaultContainerPort);
        deployment.setBranch(req.branch());
        deployment.setCommitSha(req.commitSha());
        deployment.setCommitMessage(req.commitMessage());
        deployment.setCommitAuthor(req.commitAuthor());
        deployment.setCommitTimestamp(req.commitTimestamp());
        if (req.subdomain() != null) {
            deployment.setSubdomain(req.subdomain());
        }
        deployment.setStatus(DeploymentStatus.PENDING);
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(deployment);

        String operationId = UUID.randomUUID().toString();
        eventService.record(DeploymentEventType.DEPLOY_TRIGGERED, DeploymentEventStatus.IN_PROGRESS,
                req.appName(), req, null, null, operationId);
        eventService.record(DeploymentEventType.DEPLOY_STARTED, DeploymentEventStatus.IN_PROGRESS,
                req.appName(), req, null, null, operationId);
        return new LifecycleStart(deployment, operationId);
    }

    @Transactional
    void postCreate(String appName, DeploymentStatus status,
                    CreateDeploymentRequest req, long durationMs, String error,
                    String operationId) {
        Deployment deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + appName));
        deployment.setStatus(status);
        deployment.setUpdatedAt(LocalDateTime.now());
        if (status == DeploymentStatus.RUNNING) {
            deployment.setLastDeployedAt(LocalDateTime.now());
            deployment.setBuildDurationMs(durationMs);
        }
        deploymentRepository.save(deployment);

        if (status == DeploymentStatus.RUNNING) {
            eventService.record(DeploymentEventType.DEPLOY_FINISHED, DeploymentEventStatus.SUCCESS,
                    appName, req, durationMs, null, operationId);
        } else {
            eventService.record(DeploymentEventType.FAILED, DeploymentEventStatus.FAILURE,
                    appName, req, durationMs, error, operationId);
            notificationService.sendDeployFailedAlert(appName);
        }
    }

    @Transactional
    LifecycleStart preRestart(String appName) {
        Deployment deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + appName));
        deployment.setStatus(DeploymentStatus.PENDING);
        deployment.setUpdatedAt(LocalDateTime.now());
        Deployment saved = deploymentRepository.save(deployment);
        return new LifecycleStart(saved, UUID.randomUUID().toString());
    }

    @Transactional
    Deployment postRestart(String appName, DeploymentStatus status,
                           CreateDeploymentRequest ctx, long durationMs, String error,
                           String operationId) {
        Deployment deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + appName));
        deployment.setStatus(status);
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(deployment);

        eventService.record(DeploymentEventType.RESTARTED,
                status == DeploymentStatus.RUNNING ? DeploymentEventStatus.SUCCESS : DeploymentEventStatus.FAILURE,
                appName, ctx, durationMs, error, operationId);
        return deployment;
    }

    record LifecycleStart(Deployment deployment, String operationId) {}

    @Transactional
    Deployment saveSubdomainChange(String appName, String subdomain) {
        Deployment deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + appName));
        deployment.setSubdomain(subdomain);
        deployment.setUpdatedAt(LocalDateTime.now());
        return deploymentRepository.save(deployment);
    }

    @Transactional
    boolean handlePinnedWebhook(CreateDeploymentRequest req) {
        return deploymentRepository.findByAppName(req.appName()).map(d -> {
            if (d.getPinnedImage() == null) return false;
            if (d.isSelfApp()) {
                d.setLatestKnownImage(req.imageName());
                d.setLatestKnownSha(req.commitSha());
                d.setLatestKnownMessage(req.commitMessage());
                if (req.branch() != null) d.setBranch(req.branch());
                d.setUpdatedAt(LocalDateTime.now());
                deploymentRepository.save(d);
            }
            eventService.record(DeploymentEventType.UPDATE_AVAILABLE, DeploymentEventStatus.SUCCESS,
                    req.appName(), req, null, "New version available: " + req.imageName());
            return true;
        }).orElse(false);
    }
}
