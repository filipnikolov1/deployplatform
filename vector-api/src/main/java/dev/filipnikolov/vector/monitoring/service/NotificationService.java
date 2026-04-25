package dev.filipnikolov.vector.monitoring.service;

public interface NotificationService {

    void sendDownAlert(String appName);

    void sendCrashedAlert(String appName);

    void sendRecoveredAlert(String appName);

    void sendDeployFailedAlert(String appName);
}
