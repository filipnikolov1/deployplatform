package dev.filipnikolov.vector.githubapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record InstallationRepositoriesPayload(
        String action,
        InstallationPayload.Installation installation,
        @JsonProperty("repositories_added") List<RepoInfo> repositoriesAdded,
        @JsonProperty("repositories_removed") List<RepoInfo> repositoriesRemoved
) {
    public record RepoInfo(
            @JsonProperty("full_name") String fullName,
            @JsonProperty("private") boolean isPrivate,
            @JsonProperty("default_branch") String defaultBranch
    ) {
    }
}
