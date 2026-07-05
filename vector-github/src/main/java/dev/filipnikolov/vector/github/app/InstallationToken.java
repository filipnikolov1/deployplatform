package dev.filipnikolov.vector.github.app;

import java.time.Instant;

/**
 * A cached GitHub App installation access token.
 */
public record InstallationToken(String token, Instant expiresAt) {
}
