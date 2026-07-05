package dev.filipnikolov.vector.githubapp.service;

import dev.filipnikolov.vector.githubapp.dto.InstallationPayload;
import dev.filipnikolov.vector.githubapp.dto.InstallationRepositoriesPayload;

public interface InstallationSyncService {

    void handleInstallation(InstallationPayload payload);

    void handleInstallationRepositories(InstallationRepositoriesPayload payload);
}
