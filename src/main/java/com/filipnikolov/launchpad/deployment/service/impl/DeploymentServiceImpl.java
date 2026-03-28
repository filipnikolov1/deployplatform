package com.filipnikolov.launchpad.deployment.service.impl;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.repository.DeploymentRepository;
import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import com.filipnikolov.launchpad.docker.DockerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeploymentServiceImpl implements DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final DockerService dockerService;

    public Deployment createDeployment(String appName, String repoUrl) {
        Deployment deployment = new Deployment();
        deployment.setAppName(appName);
        deployment.setRepoUrl(repoUrl);
        deployment.setImageName("filipnikolov/" + appName + ":latest");
        deployment.setStatus("PENDING");
        deployment.setCreatedAt(LocalDateTime.now());
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(deployment);

        try {
            dockerService.pullAndRun(deployment.getImageName(), appName, 3000, 3000);
            deployment.setStatus("RUNNING");
        } catch (Exception e) {
            deployment.setStatus("FAILED");
        }

        deployment.setUpdatedAt(LocalDateTime.now());
        return deploymentRepository.save(deployment);
    }
}