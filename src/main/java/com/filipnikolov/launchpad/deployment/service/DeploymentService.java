package com.filipnikolov.launchpad.deployment.service;

import com.filipnikolov.launchpad.deployment.model.Deployment;

public interface DeploymentService {
    Deployment createDeployment(String appName, String repoUrl);
}
