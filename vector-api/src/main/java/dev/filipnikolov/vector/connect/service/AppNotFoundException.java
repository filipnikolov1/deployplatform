package dev.filipnikolov.vector.connect.service;

/**
 * Thrown when a connect-status endpoint is called for an app name that has no Deployment or
 * project_service row.
 */
public class AppNotFoundException extends RuntimeException {

    public AppNotFoundException(String message) {
        super(message);
    }
}
