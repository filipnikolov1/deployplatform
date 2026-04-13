package com.filipnikolov.launchpad.deployment.service.impl;

import com.filipnikolov.launchpad.deployment.dto.CreateDeploymentRequest;
import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventStatus;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventType;
import com.filipnikolov.launchpad.deployment.model.DeploymentStatus;
import com.filipnikolov.launchpad.deployment.model.TriggerSource;
import com.filipnikolov.launchpad.deployment.repository.DeploymentRepository;
import com.filipnikolov.launchpad.deployment.service.DeploymentEventService;
import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import com.filipnikolov.launchpad.docker.service.DockerService;
import com.filipnikolov.launchpad.envvar.service.EnvVarService;
import com.filipnikolov.launchpad.exception.ResourceNotFoundException;
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

@Service
@RequiredArgsConstructor
public class DeploymentServiceImpl implements DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentServiceImpl.class);

    private final DeploymentRepository deploymentRepository;
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
        deployment.setStatus(DeploymentStatus.PENDING);
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(deployment);

        eventService.record(DeploymentEventType.DEPLOY_TRIGGERED, DeploymentEventStatus.IN_PROGRESS,
                req.appName(), req, null, null);
        eventService.record(DeploymentEventType.DEPLOY_STARTED, DeploymentEventStatus.IN_PROGRESS,
                req.appName(), req, null, null);

        try {
            Map<String, String> envVars = envVarService.getEnvVars(req.appName());
            dockerService.pullAndRun(req.imageName(), req.appName(), deployment.getContainerPort(), envVars);
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
            dockerService.pullAndRun(deployment.getImageName(), appName, deployment.getContainerPort(), envVars);
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
                trigger);
    }
}
