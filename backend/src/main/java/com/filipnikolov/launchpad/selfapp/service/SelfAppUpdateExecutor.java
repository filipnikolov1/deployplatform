package com.filipnikolov.launchpad.selfapp.service;

import com.filipnikolov.launchpad.deployment.dto.CreateDeploymentRequest;
import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventStatus;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventType;
import com.filipnikolov.launchpad.deployment.model.TriggerSource;
import com.filipnikolov.launchpad.deployment.repository.DeploymentRepository;
import com.filipnikolov.launchpad.deployment.service.DeploymentEventService;
import com.filipnikolov.launchpad.selfapp.repository.PendingSelfUpdateRepository;
import com.filipnikolov.launchpad.updater.UpdaterClient;
import com.filipnikolov.launchpad.updater.dto.UpdateResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SelfAppUpdateExecutor {

    private static final Logger log = LoggerFactory.getLogger(SelfAppUpdateExecutor.class);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventService eventService;
    private final PendingSelfUpdateRepository pendingRepo;
    private final UpdaterClient updaterClient;

    @Async
    @Transactional
    public void executeUpdate(String appName,
                              String service,
                              String targetImage,
                              String targetSha,
                              String targetMessage) {
        UpdateResponse resp;
        try {
            resp = updaterClient.update(service, targetImage);
        } catch (Exception e) {
            handleFailure(appName, targetSha, "Updater call failed: " + e.getMessage(), e);
            return;
        }

        if (!"ok".equals(resp.status())) {
            handleFailure(appName, targetSha, resp.error() != null ? resp.error() : "unknown error", null);
            return;
        }

        if (service.equals("launchpad-frontend")) {
            Deployment d = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName).orElse(null);
            if (d == null) {
                handleFailure(appName, targetSha, "Self-app not found after update trigger", null);
                return;
            }

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

            CreateDeploymentRequest historyCtx = new CreateDeploymentRequest(
                    appName, d.getRepoUrl(), targetImage, d.getContainerPort(),
                    d.getBranch(), targetSha, targetMessage,
                    d.getCommitAuthor(), null, d.getSubdomain(), TriggerSource.SELF_UPDATE);
            eventService.record(DeploymentEventType.DEPLOY_FINISHED, DeploymentEventStatus.SUCCESS,
                    appName, historyCtx, null, null);
        }
        // For launchpad-backend, the new container reconciles on startup via SelfAppBootstrap.
    }

    private void handleFailure(String appName, String targetSha, String message, Exception e) {
        pendingRepo.deleteByAppNameAndTargetSha(appName, targetSha);
        eventService.record(DeploymentEventType.UPDATE_FAILED,
                DeploymentEventStatus.FAILURE, appName, null, null, message);
        if (e != null) {
            log.error("Self-update call failed for {}: {}", appName, e.getMessage(), e);
        } else {
            log.error("Self-update failed for {}: {}", appName, message);
        }
    }
}
