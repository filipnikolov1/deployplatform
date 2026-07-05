package dev.filipnikolov.vector.githubapp.service;

/**
 * Thrown when a GitHub App-authenticated operation is attempted before the App has been
 * configured (no App ID/private key/webhook secret resolved).
 */
public class AppNotConfiguredException extends RuntimeException {

    public AppNotConfiguredException(String message, Throwable cause) {
        super(message, cause);
    }
}
