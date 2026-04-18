package com.filipnikolov.launchpad.deployment.dto;

import com.filipnikolov.launchpad.deployment.model.DeploymentEvent;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventStatus;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventType;
import com.filipnikolov.launchpad.deployment.model.TriggerSource;

import java.time.LocalDateTime;

public record DeploymentEventDto(
        Long id,
        String appName,
        DeploymentEventType eventType,
        DeploymentEventStatus status,
        String imageName,
        String branch,
        String commitSha,
        String commitMessage,
        String commitAuthor,
        Long durationMs,
        String errorMessage,
        TriggerSource triggeredBy,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        Boolean availableLocally
) {
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
