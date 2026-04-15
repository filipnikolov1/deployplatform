package com.filipnikolov.launchpad.selfapp.bootstrap;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventStatus;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventType;
import com.filipnikolov.launchpad.deployment.model.DeploymentStatus;
import com.filipnikolov.launchpad.deployment.repository.DeploymentRepository;
import com.filipnikolov.launchpad.deployment.service.DeploymentEventService;
import com.filipnikolov.launchpad.selfapp.model.PendingSelfUpdate;
import com.filipnikolov.launchpad.selfapp.repository.PendingSelfUpdateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SelfAppBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SelfAppBootstrap.class);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventService eventService;
    private final PendingSelfUpdateRepository pendingRepo;

    @Value("${LAUNCHPAD_GIT_SHA:dev}")
    private String runningSha;

    @Value("${LAUNCHPAD_GIT_MESSAGE:local build}")
    private String runningMessage;

    @PostConstruct
    public void bootstrap() {
        ensureSelfApp("launchpad-backend", "filipn123/launchpad-backend:git-" + runningSha);
        ensureSelfApp("launchpad-frontend", "filipn123/launchpad-frontend:latest");
        reconcilePendingUpdates("launchpad-backend");
    }

    private void ensureSelfApp(String appName, String image) {
        Deployment d = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseGet(() -> {
                    Deployment newD = new Deployment();
                    newD.setAppName(appName);
                    newD.setCreatedAt(LocalDateTime.now());
                    newD.setStatus(DeploymentStatus.RUNNING);
                    newD.setImageName(image);
                    newD.setContainerPort(appName.equals("launchpad-backend") ? 8082 : 3000);
                    return newD;
                });
        d.setSelfApp(true);
        if (d.getPinnedImage() == null) {
            d.setPinnedImage(image);
            d.setPinnedAt(LocalDateTime.now());
        }
        if (appName.equals("launchpad-backend")) {
            d.setCommitSha(runningSha);
            d.setCommitMessage(runningMessage);
        }
        d.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(d);

        eventService.record(DeploymentEventType.SELF_APP_BOOTSTRAPPED,
                DeploymentEventStatus.SUCCESS, appName, null, null, null);
        log.info("Self-app bootstrapped: {}", appName);
    }

    private void reconcilePendingUpdates(String appName) {
        List<PendingSelfUpdate> pending = pendingRepo.findByAppName(appName);
        for (PendingSelfUpdate p : pending) {
            if (runningSha.equals(p.getTargetSha())) {
                eventService.record(DeploymentEventType.UPDATE_SUCCESS,
                        DeploymentEventStatus.SUCCESS, appName, null, null,
                        "Reconciled after self-update to " + p.getTargetSha());
                log.info("Self-update reconciled successfully: {} -> {}", appName, p.getTargetSha());
            } else {
                eventService.record(DeploymentEventType.UPDATE_FAILED,
                        DeploymentEventStatus.FAILURE, appName, null, null,
                        "Running SHA " + runningSha + " differs from target " + p.getTargetSha());
                log.warn("Self-update reconciliation mismatch: running={} target={}",
                        runningSha, p.getTargetSha());
            }
            pendingRepo.delete(p);
        }
    }
}
