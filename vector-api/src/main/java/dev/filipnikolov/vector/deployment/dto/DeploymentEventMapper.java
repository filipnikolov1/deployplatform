package dev.filipnikolov.vector.deployment.dto;

import dev.filipnikolov.vector.deployment.model.DeploymentEvent;
import dev.filipnikolov.vector.events.dto.DeploymentEventDto;

public final class DeploymentEventMapper {

    private DeploymentEventMapper() {}

    public static DeploymentEventDto from(DeploymentEvent e) {
        return from(e, null);
    }

    public static DeploymentEventDto from(DeploymentEvent e, Boolean availableLocally) {
        return new DeploymentEventDto(
                e.getId(),
                e.getAppName(),
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
                e.getCreatedAt(),
                e.getFinishedAt(),
                availableLocally);
    }
}
