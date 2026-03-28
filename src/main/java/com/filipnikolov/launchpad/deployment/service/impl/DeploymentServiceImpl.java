package com.filipnikolov.launchpad.deployment.service.impl;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.model.DeploymentStatus;
import com.filipnikolov.launchpad.deployment.repository.DeploymentRepository;
import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import com.filipnikolov.launchpad.docker.service.DockerService;
import com.filipnikolov.launchpad.envvar.service.EnvVarService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeploymentServiceImpl implements DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentServiceImpl.class);

    private final DeploymentRepository deploymentRepository;
    private final DockerService dockerService;
    private final EnvVarService envVarService;

    @Override
    public Deployment createDeployment(String appName, String repoUrl, String imageName, int containerPort) {
        Deployment deployment = deploymentRepository.findByAppName(appName)
                .orElseGet(() -> {
                    Deployment newDeployment = new Deployment();
                    newDeployment.setAppName(appName);
                    newDeployment.setCreatedAt(LocalDateTime.now());
                    return newDeployment;
                });

        deployment.setRepoUrl(repoUrl);
        deployment.setImageName(imageName);
        deployment.setStatus(DeploymentStatus.PENDING);
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(deployment);

        try {
            Map<String, String> envVars = envVarService.getEnvVars(appName);
            dockerService.pullAndRun(imageName, appName, containerPort, envVars);
            deployment.setStatus(DeploymentStatus.RUNNING);
            log.info("Deployment successful: {}", appName);
        } catch (Exception e) {
            deployment.setStatus(DeploymentStatus.FAILED);
            log.error("Deployment failed for {}: {}", appName, e.getMessage(), e);
        }

        deployment.setUpdatedAt(LocalDateTime.now());
        return deploymentRepository.save(deployment);
    }
}
