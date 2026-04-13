package com.filipnikolov.launchpad.deployment.model;

public enum DeploymentEventType {
    DEPLOY_TRIGGERED,
    BUILD_STARTED,
    BUILD_FINISHED,
    DEPLOY_STARTED,
    DEPLOY_FINISHED,
    FAILED,
    CRASHED,
    RESTARTED,
    STOPPED,
    MANUAL_ROLLBACK,
    WEBHOOK_IGNORED,
    PIN_RELEASED
}
