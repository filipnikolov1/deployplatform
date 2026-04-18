package com.filipnikolov.launchpad.selfapp.service;

import com.filipnikolov.launchpad.deployment.dto.CreateDeploymentRequest;
import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventStatus;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventType;
import com.filipnikolov.launchpad.deployment.model.TriggerSource;
import com.filipnikolov.launchpad.deployment.repository.DeploymentRepository;
import com.filipnikolov.launchpad.deployment.service.DeploymentEventService;
import com.filipnikolov.launchpad.exception.ResourceNotFoundException;
import com.filipnikolov.launchpad.selfapp.model.PendingSelfUpdate;
import com.filipnikolov.launchpad.selfapp.repository.PendingSelfUpdateRepository;
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

        String targetImage = d.getLatestKnownImage();
        String targetSha = d.getLatestKnownSha();
        final String targetMessage = d.getLatestKnownMessage();

        eventService.record(DeploymentEventType.UPDATE_TRIGGERED,
                DeploymentEventStatus.IN_PROGRESS, appName, null, null,
                "Target: " + targetImage);

        final String service = appName.equals("launchpad-backend") ? "launchpad" : "launchpad-frontend";

        if (service.equals("launchpad")) {
            PendingSelfUpdate pending = new PendingSelfUpdate();
            pending.setUpdateId(UUID.randomUUID());
            pending.setAppName(appName);
            pending.setTargetSha(targetSha);
            pending.setTargetImage(targetImage);
            pending.setTriggeredAt(LocalDateTime.now());
            pendingRepo.save(pending);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                updateExecutor.executeUpdate(appName, service, targetImage, targetSha, targetMessage);
            }
        });
    }
}
