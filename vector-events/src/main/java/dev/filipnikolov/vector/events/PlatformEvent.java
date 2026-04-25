package dev.filipnikolov.vector.events;

import java.time.LocalDateTime;

public record PlatformEvent(
        Long id,
        String appName,
        DeploymentEventType eventType,
        DeploymentEventStatus status,
        LocalDateTime occurredAt
) {}
