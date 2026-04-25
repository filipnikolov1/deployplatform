package dev.filipnikolov.vector.deployment.service.impl;

import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.model.DeploymentEventStatus;
import dev.filipnikolov.vector.deployment.model.DeploymentEventType;
import dev.filipnikolov.vector.deployment.model.DeploymentStatus;
import dev.filipnikolov.vector.deployment.model.TriggerSource;
import dev.filipnikolov.vector.deployment.repository.DeploymentEventRepository;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.model.DeploymentEvent;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import dev.filipnikolov.vector.docker.service.DockerService;
import dev.filipnikolov.vector.envvar.service.EnvVarService;
import dev.filipnikolov.vector.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    @Value("${app.default-port:3000}")
    private int defaultContainerPort;

    @Override
    @Transactional
    public Deployment createDeployment(CreateDeploymentRequest req) {
        long startedAt = System.currentTimeMillis();

        Deployment deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(req.appName())
                .orElseGet(() -> {
                    Deployment newDeployment = new Deployment();
                    newDeployment.setAppName(req.appName());
                    newDeployment.setCreatedAt(LocalDateTime.now());
                    return newDeployment;
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

        eventService.record(DeploymentEventType.DEPLOY_TRIGGERED, DeploymentEventStatus.IN_PROGRESS,
                req.appName(), req, null, null);
        eventService.record(DeploymentEventType.DEPLOY_STARTED, DeploymentEventStatus.IN_PROGRESS,
                req.appName(), req, null, null);

        try {
            Map<String, String> envVars = envVarService.getEnvVars(req.appName());
            dockerService.pullAndRun(req.imageName(), req.appName(), deployment.getSubdomain(), deployment.getContainerPort(), envVars);
            deployment.setStatus(DeploymentStatus.RUNNING);
            long duration = System.currentTimeMillis() - startedAt;
            eventService.record(DeploymentEventType.DEPLOY_FINISHED, DeploymentEventStatus.SUCCESS,
                    req.appName(), req, duration, null);
            log.info("Deployment successful: {}", req.appName());
        } catch (Exception e) {
            deployment.setStatus(DeploymentStatus.FAILED);
            long duration = System.currentTimeMillis() - startedAt;
            eventService.record(DeploymentEventType.FAILED, DeploymentEventStatus.FAILURE,
                    req.appName(), req, duration, e.getMessage());
            log.error("Deployment failed for {}: {}", req.appName(), e.getMessage(), e);
        }

        deployment.setUpdatedAt(LocalDateTime.now());
        return deploymentRepository.save(deployment);
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
        deploymentRepository.findAll().stream()
                .filter(d -> d.getDeletedAt() != null && d.getDeletedAt().isBefore(cutoff))
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
    @Transactional
    public Deployment restartDeployment(String appName) {
        Deployment deployment = getDeployment(appName);
        long startedAt = System.currentTimeMillis();

        deployment.setStatus(DeploymentStatus.PENDING);
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(deployment);

        CreateDeploymentRequest ctx = contextFor(deployment, TriggerSource.RESTART);

        try {
            Map<String, String> envVars = envVarService.getEnvVars(appName);
            dockerService.pullAndRun(deployment.getImageName(), appName, deployment.getSubdomain(), deployment.getContainerPort(), envVars);
            deployment.setStatus(DeploymentStatus.RUNNING);
            long duration = System.currentTimeMillis() - startedAt;
            eventService.record(DeploymentEventType.RESTARTED, DeploymentEventStatus.SUCCESS,
                    appName, ctx, duration, null);
            log.info("Restart successful: {}", appName);
        } catch (Exception e) {
            deployment.setStatus(DeploymentStatus.FAILED);
            long duration = System.currentTimeMillis() - startedAt;
            eventService.record(DeploymentEventType.RESTARTED, DeploymentEventStatus.FAILURE,
                    appName, ctx, duration, e.getMessage());
            log.error("Restart failed for {}: {}", appName, e.getMessage(), e);
        }

        deployment.setUpdatedAt(LocalDateTime.now());
        return deploymentRepository.save(deployment);
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
    @Transactional
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

        try {
            dockerService.pullAndRun(target.getImageName(), appName,
                    d.getSubdomain(), d.getContainerPort(), envVarService.getEnvVars(appName));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Rollback interrupted", e);
        }

        d.setImageName(target.getImageName());
        d.setPinnedImage(target.getImageName());
        d.setPinnedAt(LocalDateTime.now());
        d.setStatus(DeploymentStatus.RUNNING);
        d.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(d);

        CreateDeploymentRequest ctx = new CreateDeploymentRequest(
                appName, d.getRepoUrl(), target.getImageName(), d.getContainerPort(),
                target.getBranch(), target.getCommitSha(), target.getCommitMessage(),
                target.getCommitAuthor(), null, d.getSubdomain(), TriggerSource.ROLLBACK);
        eventService.record(DeploymentEventType.MANUAL_ROLLBACK, DeploymentEventStatus.SUCCESS,
                appName, ctx, null, null);

        return d;
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
    @Transactional
    public Deployment updateSubdomain(String appName, String subdomain) {
        Deployment deployment = getDeployment(appName);

        if (subdomain != null) {
            if (!SUBDOMAIN_PATTERN.matcher(subdomain).matches()) {
                throw new IllegalArgumentException("Invalid subdomain");
            }
            // Reject if another live deployment already owns this subdomain
            deploymentRepository.findBySubdomainAndDeletedAtIsNull(subdomain)
                    .filter(other -> !other.getId().equals(deployment.getId()))
                    .ifPresent(other -> { throw new IllegalArgumentException("Subdomain already in use"); });
            // Reject if another live deployment has this as its app name (unless it's the same row)
            deploymentRepository.findByAppNameAndDeletedAtIsNull(subdomain)
                    .filter(other -> !other.getId().equals(deployment.getId()))
                    .ifPresent(other -> { throw new IllegalArgumentException("Subdomain already in use"); });
        }

        String oldSubdomain = deployment.getSubdomain();
        String oldDisplay = oldSubdomain != null ? oldSubdomain : "(default)";
        String newDisplay = subdomain != null ? subdomain : "(default)";

        deployment.setSubdomain(subdomain);
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(deployment);

        if (deployment.getStatus() == DeploymentStatus.RUNNING) {
            restartDeployment(appName);
        }

        eventService.record(DeploymentEventType.SUBDOMAIN_CHANGED, DeploymentEventStatus.SUCCESS,
                appName, contextFor(deployment, TriggerSource.MANUAL), null,
                "subdomain changed: " + oldDisplay + " \u2192 " + newDisplay);

        return getDeployment(appName);
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
