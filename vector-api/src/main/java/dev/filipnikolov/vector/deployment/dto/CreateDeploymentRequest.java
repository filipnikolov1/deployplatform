package dev.filipnikolov.vector.deployment.dto;

import dev.filipnikolov.vector.events.TriggerSource;

import java.time.LocalDateTime;

public record CreateDeploymentRequest(
        String appName,
        String repoUrl,
        String imageName,
        Integer containerPort,
        String branch,
        String commitSha,
        String commitMessage,
        String commitAuthor,
        LocalDateTime commitTimestamp,
        String subdomain,
        TriggerSource triggeredBy,
        String operationId
) {
    public CreateDeploymentRequest(
            String appName,
            String repoUrl,
            String imageName,
            Integer containerPort,
            String branch,
            String commitSha,
            String commitMessage,
            String commitAuthor,
            LocalDateTime commitTimestamp,
            String subdomain,
            TriggerSource triggeredBy
    ) {
        this(appName, repoUrl, imageName, containerPort, branch, commitSha, commitMessage,
                commitAuthor, commitTimestamp, subdomain, triggeredBy, null);
    }
}
