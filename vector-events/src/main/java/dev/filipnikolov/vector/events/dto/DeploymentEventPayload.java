package dev.filipnikolov.vector.events.dto;

import dev.filipnikolov.vector.events.TriggerSource;

public record DeploymentEventPayload(
        String imageName,
        String branch,
        String commitSha,
        String commitMessage,
        String commitAuthor,
        Long durationMs,
        String errorMessage,
        TriggerSource triggeredBy,
        String rollbackFromSha,
        Boolean availableLocally
) {}
