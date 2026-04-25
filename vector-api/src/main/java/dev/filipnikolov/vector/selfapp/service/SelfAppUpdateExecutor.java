package dev.filipnikolov.vector.selfapp.service;

import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.TriggerSource;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.selfapp.model.PendingSelfUpdate;
import dev.filipnikolov.vector.selfapp.model.UpdatePhase;
import dev.filipnikolov.vector.selfapp.repository.PendingSelfUpdateRepository;
import dev.filipnikolov.vector.updater.UpdaterClient;
import dev.filipnikolov.vector.updater.dto.UpdateResponse;
import dev.filipnikolov.vector.updater.dto.UpdaterStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class SelfAppUpdateExecutor {

    private static final Logger log = LoggerFactory.getLogger(SelfAppUpdateExecutor.class);
    private static final long POLL_INTERVAL_MS = 1000;
    private static final long MAX_POLL_MILLIS = 15 * 60 * 1000;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventService eventService;
    private final PendingSelfUpdateRepository pendingRepo;
    private final UpdaterClient updaterClient;
    private final TransactionTemplate txTemplate;

    public SelfAppUpdateExecutor(DeploymentRepository deploymentRepository,
                                 DeploymentEventService eventService,
                                 PendingSelfUpdateRepository pendingRepo,
                                 UpdaterClient updaterClient,
                                 PlatformTransactionManager txManager) {
        this.deploymentRepository = deploymentRepository;
        this.eventService = eventService;
        this.pendingRepo = pendingRepo;
        this.updaterClient = updaterClient;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Async
    public void executeUpdate(String appName,
                              String service,
                              String targetImage,
                              String targetSha,
                              String targetMessage,
                              UUID pendingId) {
        UpdateResponse resp;
        try {
            resp = updaterClient.update(service, targetImage);
        } catch (Exception e) {
            handleFailure(appName, targetSha, "Updater call failed: " + e.getMessage(), e);
            return;
        }

        if (!"triggered".equals(resp.status())) {
            handleFailure(appName, targetSha, resp.error() != null ? resp.error() : "unknown error", null);
            return;
        }

        long deadline = System.currentTimeMillis() + MAX_POLL_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            UpdaterStatus status = updaterClient.status(service);
            if (status == null || status.phase() == null) {
                continue;
            }

            switch (status.phase()) {
                case "recreating" -> updatePhase(pendingId, UpdatePhase.RECREATING);
                case "completed" -> {
                    finalizeSuccess(appName, service, targetImage, targetSha, targetMessage);
                    return;
                }
                case "failed" -> {
                    handleFailure(appName, targetSha,
                            status.error() != null ? status.error() : "updater reported failure", null);
                    return;
                }
                default -> { /* pulling or idle — keep polling */ }
            }
        }

        handleFailure(appName, targetSha, "Update timed out after " + (MAX_POLL_MILLIS / 1000) + "s", null);
    }

    private void updatePhase(UUID pendingId, UpdatePhase phase) {
        pendingRepo.findById(pendingId).ifPresent(p -> {
            if (p.getPhase() != phase) {
                p.setPhase(phase);
                pendingRepo.save(p);
            }
        });
    }

    private void finalizeSuccess(String appName, String service, String targetImage,
                                 String targetSha, String targetMessage) {
        // For vector-api, the new container reconciles on startup via SelfAppBootstrap.
        // This code path mostly runs for vector-web; for the API it may not be reached
        // because the container is killed during recreation.
        if (!"vector-web".equals(service)) {
            return;
        }

        txTemplate.executeWithoutResult(status -> {
            Deployment d = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName).orElse(null);
            if (d == null) {
                log.error("Self-app not found after update trigger: {}", appName);
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
            eventService.record(DeploymentEventType.DEPLOY_FINISHED,
                    DeploymentEventStatus.SUCCESS, appName, historyCtx, null, null);

            pendingRepo.deleteByAppNameAndTargetSha(appName, targetSha);
        });
    }

    private void handleFailure(String appName, String targetSha, String message, Exception e) {
        txTemplate.executeWithoutResult(status -> {
            pendingRepo.deleteByAppNameAndTargetSha(appName, targetSha);
            eventService.record(DeploymentEventType.UPDATE_FAILED,
                    DeploymentEventStatus.FAILURE, appName, null, null, message);
        });
        if (e != null) {
            log.error("Self-update call failed for {}: {}", appName, e.getMessage(), e);
        } else {
            log.error("Self-update failed for {}: {}", appName, message);
        }
    }
}
