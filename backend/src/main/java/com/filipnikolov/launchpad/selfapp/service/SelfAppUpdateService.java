package com.filipnikolov.launchpad.selfapp.service;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventStatus;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventType;
import com.filipnikolov.launchpad.deployment.repository.DeploymentRepository;
import com.filipnikolov.launchpad.deployment.service.DeploymentEventService;
import com.filipnikolov.launchpad.exception.ResourceNotFoundException;
import com.filipnikolov.launchpad.selfapp.model.PendingSelfUpdate;
import com.filipnikolov.launchpad.selfapp.model.UpdatePhase;
import com.filipnikolov.launchpad.selfapp.repository.PendingSelfUpdateRepository;
import com.filipnikolov.launchpad.updater.UpdaterClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SelfAppUpdateService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventService eventService;
    private final PendingSelfUpdateRepository pendingRepo;
    private final SelfAppUpdateExecutor updateExecutor;
    private final UpdaterClient updaterClient;

    @Transactional
    public void triggerUpdate(String appName) {
        Deployment d = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseThrow(() -> new ResourceNotFoundException("Self-app not found: " + appName));
        if (!d.isSelfApp()) {
            throw new IllegalArgumentException(appName + " is not a self-app");
        }
        if (d.getLatestKnownImage() == null || d.getLatestKnownSha() == null) {
            throw new IllegalStateException("No update available for " + appName);
        }

        pendingRepo.findFirstByAppNameOrderByTriggeredAtDesc(appName).ifPresent(p -> {
            throw new IllegalStateException("Update already in progress for " + appName);
        });

        if (!updaterClient.health()) {
            eventService.record(DeploymentEventType.UPDATER_UNREACHABLE,
                    DeploymentEventStatus.FAILURE, appName, null, null,
                    "Updater health check failed");
            throw new IllegalStateException("Updater is unreachable");
        }

        final String targetImage = d.getLatestKnownImage();
        final String targetSha = d.getLatestKnownSha();
        final String targetMessage = d.getLatestKnownMessage();

        eventService.record(DeploymentEventType.UPDATE_TRIGGERED,
                DeploymentEventStatus.IN_PROGRESS, appName, null, null,
                "Target: " + targetImage);

        final String service = appName.equals("launchpad-backend") ? "launchpad" : "launchpad-frontend";

        PendingSelfUpdate pending = new PendingSelfUpdate();
        pending.setUpdateId(UUID.randomUUID());
        pending.setAppName(appName);
        pending.setTargetSha(targetSha);
        pending.setTargetImage(targetImage);
        pending.setTriggeredAt(LocalDateTime.now());
        pending.setPhase(UpdatePhase.PULLING);
        pendingRepo.save(pending);

        final UUID pendingId = pending.getUpdateId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                updateExecutor.executeUpdate(appName, service, targetImage, targetSha, targetMessage, pendingId);
            }
        });
    }
}
