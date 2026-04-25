package dev.filipnikolov.vector.deployment.model;

/**
 * Represents the lifecycle states of a deployed application.
 */
public enum DeploymentStatus {
    PENDING,
    RUNNING,
    STOPPED,
    FAILED,
    DOWN
}
