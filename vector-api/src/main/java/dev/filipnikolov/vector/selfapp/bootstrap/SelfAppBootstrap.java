package dev.filipnikolov.vector.selfapp.bootstrap;

import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.DeploymentStatus;
import dev.filipnikolov.vector.events.TriggerSource;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.selfapp.model.PendingSelfUpdate;
import dev.filipnikolov.vector.selfapp.repository.PendingSelfUpdateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SelfAppBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SelfAppBootstrap.class);
    private static final Pattern GIT_TAG_PATTERN = Pattern.compile(":git-([A-Fa-f0-9]{7,64})$");

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventService eventService;
    private final PendingSelfUpdateRepository pendingRepo;

    @Value("${VECTOR_GIT_SHA:dev}")
    private String runningSha;

    @Value("${VECTOR_GIT_MESSAGE:local build}")
    private String runningMessage;

    @Value("${VECTOR_BACKEND_IMAGE:filipn123/vector-api:latest}")
    private String backendImage;

    @Value("${VECTOR_FRONTEND_IMAGE:filipn123/vector-web:latest}")
    private String frontendImage;

    @PostConstruct
    public void bootstrap() {
        reconcileStuckPending();
        if ("dev".equals(runningSha)) {
            log.info("Skipping self-app bootstrap (VECTOR_GIT_SHA not set — running outside compose)");
            return;
        }
        ensureSelfApp("vector-api", backendImage);
        ensureSelfApp("vector-web", frontendImage);
        reconcilePendingUpdates("vector-api");
    }

    private void reconcileStuckPending() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        deploymentRepository.findByStatusAndDeletedAtIsNull(DeploymentStatus.PENDING).stream()
                .filter(d -> d.getUpdatedAt() != null && d.getUpdatedAt().isBefore(cutoff))
                .forEach(d -> {
                    d.setStatus(DeploymentStatus.FAILED);
                    d.setUpdatedAt(LocalDateTime.now());
                    deploymentRepository.save(d);
                    eventService.record(DeploymentEventType.FAILED, DeploymentEventStatus.FAILURE,
                            d.getAppName(), null, null, "Deployment stuck in PENDING — marked FAILED on startup");
                    log.warn("Marked stuck PENDING deployment as FAILED: {}", d.getAppName());
                });
    }

    private void ensureSelfApp(String appName, String image) {
        final boolean[] created = {false};
        Deployment d = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseGet(() -> {
                    created[0] = true;
                    Deployment newD = new Deployment();
                    newD.setAppName(appName);
                    newD.setRepoUrl("https://github.com/filipnikolov1/vector-platform");
                    newD.setCreatedAt(LocalDateTime.now());
                    newD.setStatus(DeploymentStatus.RUNNING);
                    newD.setImageName(image);
                    newD.setContainerPort(appName.equals("vector-api") ? 8082 : 3000);
                    return newD;
                });
        d.setSelfApp(true);
        d.setImageName(image);
        if (d.getPinnedImage() == null) {
            d.setPinnedImage(image);
            d.setPinnedAt(LocalDateTime.now());
        }
        if (appName.equals("vector-api")) {
            d.setCommitSha(runningSha);
            d.setCommitMessage(runningMessage);
        } else {
            extractGitSha(image).ifPresent(d::setCommitSha);
        }
        d.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(d);

        if (created[0]) {
            eventService.record(DeploymentEventType.SELF_APP_BOOTSTRAPPED,
                    DeploymentEventStatus.SUCCESS, appName, null, null, null);
            log.info("Self-app bootstrapped: {}", appName);
        }
    }

    private void reconcilePendingUpdates(String appName) {
        List<PendingSelfUpdate> pending = pendingRepo.findByAppName(appName);
        for (PendingSelfUpdate p : pending) {
            if (runningSha.equals(p.getTargetSha())) {
                Deployment d = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName).orElse(null);
                if (d != null) {
                    CreateDeploymentRequest ctx = new CreateDeploymentRequest(
                            appName, d.getRepoUrl(), p.getTargetImage(), d.getContainerPort(),
                            d.getBranch(), p.getTargetSha(), runningMessage,
                            d.getCommitAuthor(), null, d.getSubdomain(), TriggerSource.SELF_UPDATE);
                    eventService.record(DeploymentEventType.DEPLOY_FINISHED,
                            DeploymentEventStatus.SUCCESS, appName, ctx, null, null);
                }
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

    private java.util.Optional<String> extractGitSha(String image) {
        if (image == null) {
            return java.util.Optional.empty();
        }
        Matcher matcher = GIT_TAG_PATTERN.matcher(image);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(matcher.group(1));
    }
}
