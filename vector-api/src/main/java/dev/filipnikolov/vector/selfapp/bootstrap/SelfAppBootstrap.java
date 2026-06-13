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

@Component
@RequiredArgsConstructor
public class SelfAppBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SelfAppBootstrap.class);
    private static final String REPO_URL = "https://github.com/filipnikolov1/vector-platform";
    private static final String VECTOR_API = "vector-api";
    private static final String VECTOR_WEB = "vector-web";
    private static final String VECTOR_ANALYZER = "vector-analyzer";
    private static final String VECTOR_UPDATER = "vector-updater";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentEventService eventService;
    private final PendingSelfUpdateRepository pendingRepo;

    @Value("${vector.self-hosted:false}")
    private boolean selfHosted;

    @Value("${VECTOR_GIT_SHA:}")
    private String envSha;

    @Value("${VECTOR_GIT_MESSAGE:}")
    private String envMessage;

    @Value("${VECTOR_GIT_BRANCH:}")
    private String envBranch;

    @Value("${VECTOR_BACKEND_IMAGE:filipn123/vector-api:latest}")
    private String backendImage;

    @Value("${VECTOR_FRONTEND_IMAGE:filipn123/vector-web:latest}")
    private String frontendImage;

    @Value("${VECTOR_ANALYZER_IMAGE:filipn123/vector-analyzer:latest}")
    private String analyzerImage;

    @Value("${VECTOR_UPDATER_IMAGE:filipn123/vector-updater:latest}")
    private String updaterImage;

    @PostConstruct
    public void bootstrap() {
        reconcileStuckPending();
        if (!selfHosted) {
            log.info("Skipping self-app bootstrap (vector.self-hosted=false)");
            return;
        }
        // vector-api can read its own image's baked-in build info from env. vector-web's
        // build info isn't visible from this process, so its row gets whatever the next
        // deploy-hook payload supplies — that's the source of truth for sibling apps.
        BuildInfo selfBuild = BuildInfo.fromEnv(envSha, envMessage, envBranch);
        ensureSelfApp(VECTOR_API, backendImage, 8082, selfBuild);
        ensureSelfApp(VECTOR_WEB, frontendImage, 3000, BuildInfo.empty());
        ensureSelfApp(VECTOR_ANALYZER, analyzerImage, 8082, BuildInfo.empty());
        ensureSelfApp(VECTOR_UPDATER, updaterImage, 8080, BuildInfo.empty());
        reconcilePendingUpdates(VECTOR_API, selfBuild);
    }

    private void reconcileStuckPending() {
        LocalDateTime pendingCutoff = LocalDateTime.now().minusMinutes(20);
        pendingRepo.findAll().stream()
                .filter(p -> !VECTOR_API.equals(p.getAppName()))
                .filter(p -> p.getTriggeredAt() != null && p.getTriggeredAt().isBefore(pendingCutoff))
                .forEach(p -> {
                    eventService.record(DeploymentEventType.UPDATE_FAILED, DeploymentEventStatus.FAILURE,
                            p.getAppName(), null, null,
                            "Self-update orphaned by restart (target " + p.getTargetSha() + ")", p.getOperationId());
                    pendingRepo.delete(p);
                    log.warn("Purged orphaned pending self-update: {} -> {}", p.getAppName(), p.getTargetSha());
                });

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

    private void ensureSelfApp(String appName, String defaultImage, int containerPort, BuildInfo info) {
        final boolean[] created = {false};
        Deployment d = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName)
                .orElseGet(() -> {
                    created[0] = true;
                    Deployment newD = new Deployment();
                    newD.setAppName(appName);
                    newD.setRepoUrl(REPO_URL);
                    newD.setCreatedAt(LocalDateTime.now());
                    newD.setStatus(DeploymentStatus.RUNNING);
                    newD.setContainerPort(containerPort);
                    return newD;
                });
        d.setSelfApp(true);
        d.setImageName(defaultImage);
        // Self-apps are unpinned by default — webhooks auto-deploy them. The user
        // can pin a self-app via rollback to lock it to a specific version, which
        // surfaces the manual Update button instead.
        info.applyTo(d);
        // If the running SHA matches what was advertised as the "latest known" version,
        // clear the latestKnown fields — otherwise the dashboard will show an Update
        // button indefinitely for an update that was already applied.
        if (info.sha() != null && info.sha().equals(d.getLatestKnownSha())) {
            d.setLatestKnownImage(null);
            d.setLatestKnownSha(null);
            d.setLatestKnownMessage(null);
        }
        d.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(d);

        if (created[0]) {
            eventService.record(DeploymentEventType.SELF_APP_BOOTSTRAPPED,
                    DeploymentEventStatus.SUCCESS, appName, null, null, null);
            log.info("Self-app bootstrapped: {}", appName);
        }
    }

    private void reconcilePendingUpdates(String appName, BuildInfo selfBuild) {
        List<PendingSelfUpdate> pending = pendingRepo.findByAppName(appName);
        String runningSha = selfBuild.sha();
        for (PendingSelfUpdate p : pending) {
            if (runningSha != null && runningSha.equals(p.getTargetSha())) {
                Deployment d = deploymentRepository.findByAppNameAndDeletedAtIsNull(appName).orElse(null);
                if (d != null) {
                    d.setImageName(p.getTargetImage());
                    d.setLatestKnownImage(null);
                    d.setLatestKnownSha(null);
                    d.setLatestKnownMessage(null);
                    d.setUpdatedAt(LocalDateTime.now());
                    deploymentRepository.save(d);

                    CreateDeploymentRequest ctx = new CreateDeploymentRequest(
                            appName, d.getRepoUrl(), p.getTargetImage(), d.getContainerPort(),
                            d.getBranch(), p.getTargetSha(), selfBuild.message(),
                            d.getCommitAuthor(), null, d.getSubdomain(), TriggerSource.SELF_UPDATE);
                    eventService.record(DeploymentEventType.DEPLOY_FINISHED,
                            DeploymentEventStatus.SUCCESS, appName, ctx, null, null, p.getOperationId());
                    eventService.record(DeploymentEventType.UPDATE_SUCCESS,
                            DeploymentEventStatus.SUCCESS, appName, ctx, null,
                            "Reconciled after self-update to " + p.getTargetSha(), p.getOperationId());
                } else {
                    eventService.record(DeploymentEventType.UPDATE_SUCCESS,
                            DeploymentEventStatus.SUCCESS, appName, null, null,
                            "Reconciled after self-update to " + p.getTargetSha(), p.getOperationId());
                }
                log.info("Self-update reconciled successfully: {} -> {}", appName, p.getTargetSha());
            } else {
                eventService.record(DeploymentEventType.UPDATE_FAILED,
                        DeploymentEventStatus.FAILURE, appName, null, null,
                        "Running SHA " + runningSha + " differs from target " + p.getTargetSha(),
                        p.getOperationId());
                log.warn("Self-update reconciliation mismatch: running={} target={}",
                        runningSha, p.getTargetSha());
            }
            pendingRepo.delete(p);
        }
    }

    private record BuildInfo(String sha, String message, String branch) {
        static BuildInfo empty() {
            return new BuildInfo(null, null, null);
        }

        static BuildInfo fromEnv(String sha, String message, String branch) {
            return new BuildInfo(sanitize(sha), sanitize(message), sanitize(branch));
        }

        // Filter Dockerfile placeholder defaults so the DB never carries fake metadata
        // when an image is built without proper build-args (e.g. local compose build).
        private static String sanitize(String value) {
            if (value == null || value.isBlank()) return null;
            if ("dev".equals(value) || "local".equals(value) || "local build".equals(value)) return null;
            return value;
        }

        void applyTo(Deployment d) {
            if (sha != null) d.setCommitSha(sha);
            if (message != null) d.setCommitMessage(message);
            if (branch != null) d.setBranch(branch);
        }
    }
}
