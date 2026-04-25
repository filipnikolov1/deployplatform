package com.filipnikolov.launchpad.deployment.service;

import com.filipnikolov.launchpad.deployment.dto.CreateDeploymentRequest;
import com.filipnikolov.launchpad.deployment.model.DeploymentEvent;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventStatus;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventType;

import java.util.List;
import java.util.Optional;

public interface DeploymentEventService {

    DeploymentEvent record(DeploymentEventType type,
                           DeploymentEventStatus status,
                           String appName,
                           CreateDeploymentRequest ctx,
                           Long durationMs,
                           String errorMessage);

    List<DeploymentEvent> listForApp(String appName, int limit);

    List<DeploymentEvent> listGlobal(int limit);

    Optional<DeploymentEvent> latestForApp(String appName);
}
