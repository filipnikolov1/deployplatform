package dev.filipnikolov.vector.connect.service;

/**
 * Thrown when a connect request targets a repo whose installation is not APPROVED.
 */
public class InstallationNotApprovedException extends RuntimeException {

    public InstallationNotApprovedException(String message) {
        super(message);
    }
}
