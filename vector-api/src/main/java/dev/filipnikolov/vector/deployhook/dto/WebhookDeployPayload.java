package dev.filipnikolov.vector.deployhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WebhookDeployPayload(
        Long timestamp,
        String image,
        @JsonProperty("app_name") String appName,
        @JsonProperty("repo_url") String repoUrl,
        Integer port,
        String branch,
        @JsonProperty("commit_sha") String commitSha,
        @JsonProperty("commit_message") String commitMessage,
        @JsonProperty("commit_author") String commitAuthor,
        @JsonProperty("commit_timestamp") Long commitTimestamp
) {}
