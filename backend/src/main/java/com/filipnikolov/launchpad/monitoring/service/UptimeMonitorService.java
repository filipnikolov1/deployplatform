package com.filipnikolov.launchpad.monitoring.service;

/**
 * Periodically checks whether each deployment's Docker container is still
 * running. This is a container-liveness check, not an HTTP health probe —
 * an app whose process is running but returning 500s is still considered
 * "up" by this service. Marks non-running containers as DOWN and sends
 * alerts via the NotificationService.
 */
public interface UptimeMonitorService {

    /**
     * Runs a container-liveness check on all deployments with status
     * RUNNING. Called automatically every 60 seconds by the scheduler.
     */
    void checkAll();
}
