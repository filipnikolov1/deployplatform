package dev.filipnikolov.vector.exception;

/**
 * Thrown when a requested resource (e.g., deployment, env var) does not exist.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
