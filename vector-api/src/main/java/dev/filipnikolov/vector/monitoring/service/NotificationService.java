package dev.filipnikolov.vector.monitoring.service;

/**
 * Sends alert notifications when deployed apps go down.
 * Currently uses Resend for email delivery.
 */
public interface NotificationService {

    /**
     * Sends a downtime alert email for the given application.
     *
     * @param appName the name of the app that is unreachable
     */
    void sendDownAlert(String appName);
}
