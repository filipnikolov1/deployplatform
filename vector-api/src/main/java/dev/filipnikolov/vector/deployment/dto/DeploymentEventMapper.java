package dev.filipnikolov.vector.deployment.dto;

import dev.filipnikolov.vector.deployment.model.DeploymentEvent;
import dev.filipnikolov.vector.events.PlatformEventEnvelope;
import dev.filipnikolov.vector.events.dto.DeploymentEventDto;
import dev.filipnikolov.vector.events.dto.DeploymentEventPayload;

import java.time.ZoneOffset;

public final class DeploymentEventMapper {

    private DeploymentEventMapper() {}

    public static DeploymentEventDto from(DeploymentEvent e) {
        return from(e, null);
    }

    public static DeploymentEventDto from(DeploymentEvent e, Boolean availableLocally) {
        return new DeploymentEventDto(
                e.getId(),
                e.getAppName(),
                e.getOperationId(),
                e.getEventType(),
                e.getStatus(),
                e.getImageName(),
                e.getBranch(),
                e.getCommitSha(),
                e.getCommitMessage(),
                e.getCommitAuthor(),
                e.getDurationMs(),
                e.getErrorMessage(),
                e.getTriggeredBy(),
                e.getRollbackFromSha(),
                e.getCreatedAt(),
                e.getFinishedAt(),
                availableLocally);
    }

    public static PlatformEventEnvelope envelopeFrom(DeploymentEvent e) {
        return new PlatformEventEnvelope(
                1,
                e.getId(),
                e.getOperationId(),
                e.getAppName(),
                e.getEventType(),
                e.getStatus(),
                e.getCreatedAt().toInstant(ZoneOffset.UTC),
                new DeploymentEventPayload(
                        e.getImageName(),
                        e.getBranch(),
                        e.getCommitSha(),
                        e.getCommitMessage(),
                        e.getCommitAuthor(),
                        e.getDurationMs(),
                        e.getErrorMessage(),
                        e.getTriggeredBy(),
                        e.getRollbackFromSha(),
                        null));
    }
}
