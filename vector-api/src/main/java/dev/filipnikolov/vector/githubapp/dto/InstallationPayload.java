package dev.filipnikolov.vector.githubapp.dto;

public record InstallationPayload(
        String action,
        Installation installation
) {
    public record Installation(
            Long id,
            Account account
    ) {
    }

    public record Account(
            String login,
            String type
    ) {
    }
}
