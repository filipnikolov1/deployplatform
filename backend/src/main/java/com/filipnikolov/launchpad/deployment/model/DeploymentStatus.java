package com.filipnikolov.launchpad.deployment.model;

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
