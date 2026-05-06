package dev.filipnikolov.vector.events;

import dev.filipnikolov.vector.events.dto.DeploymentEventPayload;

import java.time.Instant;

public record PlatformEventEnvelope(
        int version,
        long id,
        String operationId,
        String appName,
        DeploymentEventType type,
        DeploymentEventStatus status,
        Instant occurredAt,
        DeploymentEventPayload payload
) {}
