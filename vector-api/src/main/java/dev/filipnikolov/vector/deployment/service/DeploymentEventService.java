package dev.filipnikolov.vector.deployment.service;

import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.DeploymentEvent;
import dev.filipnikolov.vector.deployment.model.DeploymentEventStatus;
import dev.filipnikolov.vector.deployment.model.DeploymentEventType;

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
