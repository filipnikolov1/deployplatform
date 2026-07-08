package dev.filipnikolov.vector.connect.service;

/**
 * Thrown when {@code POST /api/connect/{appName}/redeploy} is called for a CUSTOM-lane repo —
 * Vector never dispatches a workflow it doesn't manage.
 */
public class CustomWorkflowRedeployException extends RuntimeException {

    public CustomWorkflowRedeployException(String message) {
        super(message);
    }
}
