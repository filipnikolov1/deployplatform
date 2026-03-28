package com.filipnikolov.launchpad.monitoring.service;

/**
 * Periodically checks the health of all running apps by pinging their
 * Traefik-routed URLs. Marks unresponsive apps as DOWN and sends alerts.
 */
public interface UptimeMonitorService {

    /**
     * Runs a health check on all deployments with status RUNNING.
     * Called automatically every 60 seconds by the scheduler.
     */
    void checkAll();
}
