package dev.filipnikolov.vector.deployment.service.impl;

import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.model.DeploymentEvent;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.DeploymentStatus;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.exception.ResourceNotFoundException;
import dev.filipnikolov.vector.monitoring.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
        // A new app's name becomes its default Traefik host — refuse if another
        // app already routes that host via a custom subdomain.
        if (deployment.getId() == null) {
            deploymentRepository.findBySubdomainAndDeletedAtIsNull(req.appName())
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("App name '" + req.appName()
                                + "' collides with the subdomain of app '" + other.getAppName() + "'");
                    });
        }
        deployment.setRepoUrl(req.repoUrl());
        deployment.setImageName(req.imageName());
        // The webhook path always sets a port, so this fallback only fires for non-webhook callers.
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

    @Transactional
    Deployment finalizeRollback(String appName, DeploymentEvent target,
                                CreateDeploymentRequest ctx, String rollbackFromSha,
                                String operationId) {
        Deployment d = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + appName));
        d.setImageName(target.getImageName());
        d.setPinnedImage(target.getImageName());
        d.setPinnedAt(LocalDateTime.now());
        d.setCommitSha(target.getCommitSha());
        d.setCommitMessage(target.getCommitMessage());
        d.setCommitAuthor(target.getCommitAuthor());
        if (target.getBranch() != null) d.setBranch(target.getBranch());
        d.setStatus(DeploymentStatus.RUNNING);
        d.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(d);

        DeploymentEvent rollbackEvent = eventService.record(DeploymentEventType.MANUAL_ROLLBACK,
                DeploymentEventStatus.SUCCESS, appName, ctx, null, null, operationId);
        rollbackEvent.setRollbackFromSha(rollbackFromSha);
        return d;
    }

    record LifecycleStart(Deployment deployment, String operationId) {}

    @Transactional
    Deployment saveSubdomainChange(String appName, String subdomain) {
        Deployment deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + appName));
        if (subdomain != null) {
            Long currentId = deployment.getId();
            deploymentRepository.findBySubdomainAndDeletedAtIsNull(subdomain)
                    .filter(other -> !other.getId().equals(currentId))
                    .ifPresent(other -> { throw new IllegalArgumentException("Subdomain already in use"); });
            deploymentRepository.findByAppNameAndDeletedAtIsNull(subdomain)
                    .filter(other -> !other.getId().equals(currentId))
                    .ifPresent(other -> { throw new IllegalArgumentException("Subdomain already in use"); });
        }
        deployment.setSubdomain(subdomain);
        deployment.setUpdatedAt(LocalDateTime.now());
        try {
            return deploymentRepository.saveAndFlush(deployment);
        } catch (DataIntegrityViolationException e) {
            // Concurrent updateSubdomain raced past the check above; unique index caught it.
            throw new IllegalArgumentException("Subdomain already in use", e);
        }
    }

    @Transactional
    boolean handlePinnedWebhook(CreateDeploymentRequest req) {
        return deploymentRepository.findByAppName(req.appName()).map(d -> {
            // Self-apps always go through this branch — they have a separate update
            // path (updater binary) that preserves docker-compose config, so we never
            // fall through to createDeployment for them. handleWebhookDeployAsync
            // looks at isSelfApp + pinnedImage to decide whether to auto-trigger.
            boolean isSelfApp = d.isSelfApp();
            if (d.getPinnedImage() == null && !isSelfApp) return false;
            if (isSelfApp) {
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

    /**
     * Returns true if the app should auto-trigger an update after recording UPDATE_AVAILABLE.
     * Auto-trigger fires for unpinned non-API self-apps that the updater can handle. vector-api
     * stays manual (kill-self problem), pinned apps stay manual (user opted out), vector-updater
     * is excluded because it can't recreate itself via the updater binary.
     */
    boolean shouldAutoTriggerSelfUpdate(String appName) {
        return deploymentRepository.findByAppName(appName).map(d ->
                d.isSelfApp()
                        && d.getPinnedImage() == null
                        && !"vector-api".equals(appName)
                        && !"vector-updater".equals(appName)
        ).orElse(false);
    }
}
