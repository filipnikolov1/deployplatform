package dev.filipnikolov.vector.deployment.service.impl;

import dev.filipnikolov.vector.common.lock.ActionLockService;
import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.DeploymentStatus;
import dev.filipnikolov.vector.events.TriggerSource;
import dev.filipnikolov.vector.deployment.repository.DeploymentEventRepository;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.model.DeploymentEvent;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import dev.filipnikolov.vector.docker.service.DockerService;
import dev.filipnikolov.vector.envvar.service.EnvVarService;
import dev.filipnikolov.vector.exception.ResourceNotFoundException;
import dev.filipnikolov.vector.progress.ProgressHub;
import dev.filipnikolov.vector.selfapp.service.SelfAppUpdateService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DeploymentServiceImpl implements DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentServiceImpl.class);
    private static final Pattern SUBDOMAIN_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventRepository eventRepository;
    private final DockerService dockerService;
    private final EnvVarService envVarService;
    private final DeploymentEventService eventService;
    private final DeploymentTransactionHelper txHelper;
    private final ProgressHub progressHub;
    private final SelfAppUpdateService selfAppUpdateService;

    @Override
    public Deployment createDeployment(CreateDeploymentRequest req) {
        long startedAt = System.currentTimeMillis();
        DeploymentTransactionHelper.LifecycleStart start = txHelper.preCreate(req);
        Deployment deployment = start.deployment();
        String operationId = start.operationId();
        progressHub.start(operationId);
        try {
            Map<String, String> envVars = envVarService.getEnvVars(req.appName());
            eventService.record(DeploymentEventType.PULL_STARTED, DeploymentEventStatus.IN_PROGRESS,
                    req.appName(), req, null, null, operationId);
            dockerService.pullAndRun(req.imageName(), req.appName(),
                    deployment.getSubdomain(), deployment.getContainerPort(), envVars,
                    frame -> progressHub.emit(operationId, frame));
            eventService.record(DeploymentEventType.PULL_FINISHED, DeploymentEventStatus.SUCCESS,
                    req.appName(), req, null, null, operationId);
            eventService.record(DeploymentEventType.CONTAINER_CREATING, DeploymentEventStatus.IN_PROGRESS,
                    req.appName(), req, null, null, operationId);
            eventService.record(DeploymentEventType.CONTAINER_STARTED, DeploymentEventStatus.IN_PROGRESS,
                    req.appName(), req, null, null, operationId);
            long duration = System.currentTimeMillis() - startedAt;
            txHelper.postCreate(req.appName(), DeploymentStatus.RUNNING, req, duration, null, operationId);
            log.info("Deployment successful: {}", req.appName());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startedAt;
            txHelper.postCreate(req.appName(), DeploymentStatus.FAILED, req, duration, e.getMessage(), operationId);
            log.error("Deployment failed for {}: {}", req.appName(), e.getMessage(), e);
        } finally {
            progressHub.end(operationId);
        }
        return getDeployment(req.appName());
    }

    @Override
    public List<Deployment> getAllDeployments() {
        return deploymentRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public Deployment getDeployment(String appName) {
        return deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + appName));
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void hardDeleteExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        deploymentRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(cutoff)
                .forEach(d -> {
                    try {
                        dockerService.stopAndRemoveContainer(d.getAppName());
                    } catch (Exception e) {
                        log.warn("Failed to stop container during hard-delete of {}: {}", d.getAppName(), e.getMessage());
                    }
                    deploymentRepository.delete(d);
                    log.info("Hard-deleted expired deployment: {}", d.getAppName());
                });
    }

    @Override
    public Deployment restartDeployment(String appName) {
        long startedAt = System.currentTimeMillis();
        DeploymentTransactionHelper.LifecycleStart start = txHelper.preRestart(appName);
        Deployment deployment = start.deployment();
        String operationId = start.operationId();
        progressHub.start(operationId);
        CreateDeploymentRequest ctx = contextFor(deployment, TriggerSource.RESTART);
        try {
            Map<String, String> envVars = envVarService.getEnvVars(appName);
            eventService.record(DeploymentEventType.PULL_STARTED, DeploymentEventStatus.IN_PROGRESS,
                    appName, ctx, null, null, operationId);
            dockerService.pullAndRun(deployment.getImageName(), appName,
                    deployment.getSubdomain(), deployment.getContainerPort(), envVars,
                    frame -> progressHub.emit(operationId, frame));
            eventService.record(DeploymentEventType.PULL_FINISHED, DeploymentEventStatus.SUCCESS,
                    appName, ctx, null, null, operationId);
            eventService.record(DeploymentEventType.CONTAINER_CREATING, DeploymentEventStatus.IN_PROGRESS,
                    appName, ctx, null, null, operationId);
            eventService.record(DeploymentEventType.CONTAINER_STARTED, DeploymentEventStatus.IN_PROGRESS,
                    appName, ctx, null, null, operationId);
            long duration = System.currentTimeMillis() - startedAt;
            txHelper.postRestart(appName, DeploymentStatus.RUNNING, ctx, duration, null, operationId);
            log.info("Restart successful: {}", appName);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startedAt;
            txHelper.postRestart(appName, DeploymentStatus.FAILED, ctx, duration, e.getMessage(), operationId);
            log.error("Restart failed for {}: {}", appName, e.getMessage(), e);
        } finally {
            progressHub.end(operationId);
        }
        return getDeployment(appName);
    }

    @Override
    @Transactional
    public Deployment stopDeployment(String appName) {
        Deployment deployment = getDeployment(appName);

        dockerService.stopAndRemoveContainer(appName);
        deployment.setStatus(DeploymentStatus.STOPPED);
        deployment.setUpdatedAt(LocalDateTime.now());
        eventService.record(DeploymentEventType.STOPPED, DeploymentEventStatus.SUCCESS,
                appName, contextFor(deployment, TriggerSource.MANUAL), null, null);
        log.info("Stopped: {}", appName);

        return deploymentRepository.save(deployment);
    }

    @Override
    @Transactional
    public Deployment softDelete(String appName) {
        Deployment d = getDeployment(appName);
        d.setDeletedAt(LocalDateTime.now());
        return deploymentRepository.save(d);
    }

    @Override
    @Transactional
    public Deployment restore(String appName) {
        Deployment d = deploymentRepository.findByAppName(appName)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + appName));
        if (d.getDeletedAt() == null) return d;
        if (d.getDeletedAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new IllegalStateException("Undo window expired");
        }
        d.setDeletedAt(null);
        return deploymentRepository.save(d);
    }

    @Override
    public Deployment rollback(String appName, Long eventId) {
        DeploymentEvent target = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        boolean rollbackableType = target.getEventType() == DeploymentEventType.DEPLOY_FINISHED
                || target.getEventType() == DeploymentEventType.MANUAL_ROLLBACK;
        if (!target.getAppName().equals(appName)
                || !rollbackableType
                || target.getStatus() != DeploymentEventStatus.SUCCESS
                || target.getImageName() == null
                || target.getImageName().isBlank()) {
            throw new IllegalArgumentException("Invalid rollback target");
        }
        Deployment d = getDeployment(appName);
        String rollbackFromSha = d.getCommitSha();
        String operationId = UUID.randomUUID().toString();
        progressHub.start(operationId);

        CreateDeploymentRequest ctx = new CreateDeploymentRequest(
                appName, d.getRepoUrl(), target.getImageName(), d.getContainerPort(),
                target.getBranch(), target.getCommitSha(), target.getCommitMessage(),
                target.getCommitAuthor(), null, d.getSubdomain(), TriggerSource.ROLLBACK);

        try {
            eventService.record(DeploymentEventType.PULL_STARTED, DeploymentEventStatus.IN_PROGRESS,
                    appName, ctx, null, null, operationId);
            dockerService.pullAndRun(target.getImageName(), appName,
                    d.getSubdomain(), d.getContainerPort(), envVarService.getEnvVars(appName),
                    frame -> progressHub.emit(operationId, frame));
            eventService.record(DeploymentEventType.PULL_FINISHED, DeploymentEventStatus.SUCCESS,
                    appName, ctx, null, null, operationId);
            eventService.record(DeploymentEventType.CONTAINER_CREATING, DeploymentEventStatus.IN_PROGRESS,
                    appName, ctx, null, null, operationId);
            eventService.record(DeploymentEventType.CONTAINER_STARTED, DeploymentEventStatus.IN_PROGRESS,
                    appName, ctx, null, null, operationId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            eventService.record(DeploymentEventType.FAILED, DeploymentEventStatus.FAILURE,
                    appName, ctx, null, "Rollback interrupted", operationId);
            throw new RuntimeException("Rollback interrupted", e);
        } catch (RuntimeException e) {
            eventService.record(DeploymentEventType.FAILED, DeploymentEventStatus.FAILURE,
                    appName, ctx, null, e.getMessage(), operationId);
            throw e;
        } finally {
            progressHub.end(operationId);
        }

        return txHelper.finalizeRollback(appName, target, ctx, rollbackFromSha, operationId);
    }

    @Override
    @Transactional
    public Deployment unpin(String appName) {
        Deployment d = getDeployment(appName);
        d.setPinnedImage(null);
        d.setPinnedAt(null);
        d.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(d);
        eventService.record(DeploymentEventType.PIN_RELEASED, DeploymentEventStatus.SUCCESS,
                appName, null, null, null);
        return d;
    }

    @Override
    public Deployment updateSubdomain(String appName, String subdomain) {
        if (subdomain != null && !SUBDOMAIN_PATTERN.matcher(subdomain).matches()) {
            throw new IllegalArgumentException("Invalid subdomain");
        }

        Deployment current = getDeployment(appName);
        String oldSubdomain = current.getSubdomain();
        String oldDisplay = oldSubdomain != null ? oldSubdomain : "(default)";
        String newDisplay = subdomain != null ? subdomain : "(default)";

        Deployment deployment = txHelper.saveSubdomainChange(appName, subdomain);

        if (deployment.getStatus() == DeploymentStatus.RUNNING) {
            try {
                restartDeployment(appName);
            } catch (RuntimeException e) {
                log.error("Subdomain row updated but restart failed for {}: {}", appName, e.getMessage(), e);
                throw new IllegalStateException(
                        "Subdomain saved but container restart failed; retry restart manually", e);
            }
        }

        eventService.record(DeploymentEventType.SUBDOMAIN_CHANGED, DeploymentEventStatus.SUCCESS,
                appName, contextFor(deployment, TriggerSource.MANUAL), null,
                "subdomain changed: " + oldDisplay + " → " + newDisplay);

        return getDeployment(appName);
    }

    @Override
    @Async("deployExecutor")
    public void handleWebhookDeployAsync(CreateDeploymentRequest req, ActionLockService.LockHandle lock) {
        try {
            if (txHelper.handlePinnedWebhook(req)) {
                if (txHelper.shouldAutoTriggerSelfUpdate(req.appName())) {
                    try {
                        selfAppUpdateService.triggerUpdate(req.appName());
                        log.info("Auto-triggered self-update for {} — new image {}", req.appName(), req.imageName());
                    } catch (Exception e) {
                        log.error("Auto-trigger failed for {}: {}", req.appName(), e.getMessage(), e);
                    }
                } else {
                    log.info("Update available for {} — new image {}", req.appName(), req.imageName());
                }
                return;
            }
            createDeployment(req);
        } finally {
            lock.close();
        }
    }

    private CreateDeploymentRequest contextFor(Deployment d, TriggerSource trigger) {
        return new CreateDeploymentRequest(
                d.getAppName(),
                d.getRepoUrl(),
                d.getImageName(),
                d.getContainerPort(),
                d.getBranch(),
                d.getCommitSha(),
                d.getCommitMessage(),
                d.getCommitAuthor(),
                d.getCommitTimestamp(),
                d.getSubdomain(),
                trigger);
    }
}
