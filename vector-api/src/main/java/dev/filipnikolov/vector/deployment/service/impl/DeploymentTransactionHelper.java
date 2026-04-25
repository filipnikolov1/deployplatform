package dev.filipnikolov.vector.deployment.service.impl;

import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.model.DeploymentEventStatus;
import dev.filipnikolov.vector.deployment.model.DeploymentEventType;
import dev.filipnikolov.vector.deployment.model.DeploymentStatus;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class DeploymentTransactionHelper {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventService eventService;

    @Value("${app.default-port:3000}")
    private int defaultContainerPort;

    @Transactional
    Deployment preCreate(CreateDeploymentRequest req) {
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

        eventService.record(DeploymentEventType.DEPLOY_TRIGGERED, DeploymentEventStatus.IN_PROGRESS,
                req.appName(), req, null, null);
        eventService.record(DeploymentEventType.DEPLOY_STARTED, DeploymentEventStatus.IN_PROGRESS,
                req.appName(), req, null, null);
        return deployment;
    }
}
