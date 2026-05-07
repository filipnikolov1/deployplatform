package dev.filipnikolov.vector.events.dto;

import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.TriggerSource;

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
        String rollbackFromSha,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        Boolean availableLocally
) {}
