package com.filipnikolov.launchpad.deployment.service.impl;

import com.filipnikolov.launchpad.deployment.dto.CreateDeploymentRequest;
import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.model.DeploymentStatus;
import com.filipnikolov.launchpad.deployment.repository.DeploymentRepository;
import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import com.filipnikolov.launchpad.docker.service.DockerService;
import com.filipnikolov.launchpad.envvar.service.EnvVarService;
import com.filipnikolov.launchpad.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    @Value("${app.default-port:3000}")
    private int defaultContainerPort;

    @Override
    public Deployment createDeployment(CreateDeploymentRequest req) {
        Deployment deployment = deploymentRepository.findByAppName(req.appName())
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

        try {
            Map<String, String> envVars = envVarService.getEnvVars(req.appName());
            dockerService.pullAndRun(req.imageName(), req.appName(), deployment.getContainerPort(), envVars);
            deployment.setStatus(DeploymentStatus.RUNNING);
            log.info("Deployment successful: {}", req.appName());
        } catch (Exception e) {
            deployment.setStatus(DeploymentStatus.FAILED);
            log.error("Deployment failed for {}: {}", req.appName(), e.getMessage(), e);
        }

        deployment.setUpdatedAt(LocalDateTime.now());
        return deploymentRepository.save(deployment);
    }

    @Override
    public List<Deployment> getAllDeployments() {
        return deploymentRepository.findAll();
    }

    @Override
    public Deployment getDeployment(String appName) {
        return deploymentRepository.findByAppName(appName)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + appName));
    }

    @Override
    public Deployment restartDeployment(String appName) {
        Deployment deployment = getDeployment(appName);

        deployment.setStatus(DeploymentStatus.PENDING);
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(deployment);

        try {
            Map<String, String> envVars = envVarService.getEnvVars(appName);
            dockerService.pullAndRun(deployment.getImageName(), appName, deployment.getContainerPort(), envVars);
            deployment.setStatus(DeploymentStatus.RUNNING);
            log.info("Restart successful: {}", appName);
        } catch (Exception e) {
            deployment.setStatus(DeploymentStatus.FAILED);
            log.error("Restart failed for {}: {}", appName, e.getMessage(), e);
        }

        deployment.setUpdatedAt(LocalDateTime.now());
        return deploymentRepository.save(deployment);
    }

    @Override
    public Deployment stopDeployment(String appName) {
        Deployment deployment = getDeployment(appName);

        dockerService.stopAndRemoveContainer(appName);
        deployment.setStatus(DeploymentStatus.STOPPED);
        deployment.setUpdatedAt(LocalDateTime.now());
        log.info("Stopped: {}", appName);

        return deploymentRepository.save(deployment);
    }
}
