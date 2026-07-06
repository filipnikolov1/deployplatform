package dev.filipnikolov.vector.connect.service;

/**
 * Thrown when a connect request's app name collides with an existing deployment or
 * project_service row.
 */
public class AppNameTakenException extends RuntimeException {

    public AppNameTakenException(String message) {
        super(message);
    }
}
