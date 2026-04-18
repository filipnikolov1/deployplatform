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
import com.filipnikolov.launchpad.updater.UpdaterClient;
import com.filipnikolov.launchpad.updater.dto.UpdateResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SelfAppUpdateService {

    private static final Logger log = LoggerFactory.getLogger(SelfAppUpdateService.class);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventService eventService;
    private final PendingSelfUpdateRepository pendingRepo;
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

        String targetImage = d.getLatestKnownImage();
        String targetSha = d.getLatestKnownSha();
        String targetMessage = d.getLatestKnownMessage();

        if (!updaterClient.health()) {
            eventService.record(DeploymentEventType.UPDATER_UNREACHABLE,
                    DeploymentEventStatus.FAILURE, appName, null, null,
                    "Updater health check failed");
            throw new IllegalStateException("Updater is unreachable");
        }

        eventService.record(DeploymentEventType.UPDATE_TRIGGERED,
                DeploymentEventStatus.IN_PROGRESS, appName, null, null,
                "Target: " + targetImage);

        String service = appName.equals("launchpad-backend") ? "launchpad" : "launchpad-frontend";

        PendingSelfUpdate pending = null;
        if (service.equals("launchpad")) {
            pending = new PendingSelfUpdate();
            pending.setUpdateId(UUID.randomUUID());
            pending.setAppName(appName);
            pending.setTargetSha(targetSha);
            pending.setTargetImage(targetImage);
            pending.setTriggeredAt(LocalDateTime.now());
            pendingRepo.save(pending);
        }

        UpdateResponse resp;
        try {
            resp = updaterClient.update(service, targetImage);
        } catch (Exception e) {
            if (pending != null) {
                pendingRepo.delete(pending);
            }
            eventService.record(DeploymentEventType.UPDATE_FAILED,
                    DeploymentEventStatus.FAILURE, appName, null, null,
                    "Updater call failed: " + e.getMessage());
            log.error("Self-update call failed for {}: {}", appName, e.getMessage(), e);
            return;
        }

        if (!"ok".equals(resp.status())) {
            if (pending != null) {
                pendingRepo.delete(pending);
            }
            eventService.record(DeploymentEventType.UPDATE_FAILED,
                    DeploymentEventStatus.FAILURE, appName, null, null,
                    resp.error() != null ? resp.error() : "unknown error");
            log.error("Self-update failed for {}: {}", appName, resp.error());
            return;
        }

        if (service.equals("launchpad-frontend")) {
            d.setImageName(targetImage);
            d.setCommitSha(targetSha);
            d.setPinnedImage(targetImage);
            d.setPinnedAt(LocalDateTime.now());
            d.setLatestKnownImage(null);
            d.setLatestKnownSha(null);
            d.setLatestKnownMessage(null);
            d.setUpdatedAt(LocalDateTime.now());
            deploymentRepository.save(d);
            eventService.record(DeploymentEventType.UPDATE_SUCCESS,
                    DeploymentEventStatus.SUCCESS, appName, null, null,
                    "Updated to " + targetImage);

            // Record DEPLOY_FINISHED so the update appears in deploy history and is a rollback target
            CreateDeploymentRequest historyCtx = new CreateDeploymentRequest(
                    appName, d.getRepoUrl(), targetImage, d.getContainerPort(),
                    d.getBranch(), targetSha, targetMessage,
                    d.getCommitAuthor(), null, d.getSubdomain(), TriggerSource.SELF_UPDATE);
            eventService.record(DeploymentEventType.DEPLOY_FINISHED, DeploymentEventStatus.SUCCESS,
                    appName, historyCtx, null, null);
        }
        // For launchpad-backend, the new container will reconcile on startup via SelfAppBootstrap.
    }
}
