package dev.filipnikolov.vector.deployhook.controller;

import dev.filipnikolov.vector.common.lock.ActionLockService;
import dev.filipnikolov.vector.deployhook.dto.WebhookDeployPayload;
import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.events.TriggerSource;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import dev.filipnikolov.vector.deployhook.auth.service.DeployHookAuthService;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/deploy-hook")
public class DeployHookController {

    private static final Logger log = LoggerFactory.getLogger(DeployHookController.class);
    private static final Pattern VALID_APP_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$");
    private static final long REPLAY_WINDOW_MS = 5 * 60 * 1000;

    private final DeploymentService deploymentService;
    private final DeployHookAuthService deployHookAuthService;
    private final ActionLockService actionLockService;
    private final ObjectMapper objectMapper;

    @Value("${app.default-port:3000}")
    private int defaultContainerPort;

    public DeployHookController(DeploymentService deploymentService,
                                DeployHookAuthService deployHookAuthService,
                                ActionLockService actionLockService,
                                ObjectMapper springObjectMapper) {
        this.deploymentService = deploymentService;
        this.deployHookAuthService = deployHookAuthService;
        this.actionLockService = actionLockService;
        // Strict copy: reject string-to-number coercion so mistyped numerics ("timestamp":"1",
        // "port":"abc") become Jackson binding errors → 400, not ClassCastException → 500.
        this.objectMapper = springObjectMapper.copy().disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }

    @PostMapping
    public ResponseEntity<?> handleDeploy(
            @RequestHeader("X-Signature-256") String signature,
            @RequestHeader(value = "X-Vector-Trigger", required = false) String triggerHeader,
            @RequestBody String rawBody) {

        // HMAC must hash the exact received bytes — keep rawBody as String and parse below.
        if (!deployHookAuthService.isValidSignature(rawBody, signature)) {
            return ResponseEntity.status(401).build();
        }

        if (!deployHookAuthService.registerSignatureOnce(signature)) {
            log.warn("Duplicate deploy-hook request rejected (replay protection)");
            return ResponseEntity.status(409).body(Map.of("error", "duplicate request"));
        }

        WebhookDeployPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookDeployPayload.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload", e);
        }

        if (payload.timestamp() == null) {
            return ResponseEntity.badRequest().build();
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - payload.timestamp()) > REPLAY_WINDOW_MS) {
            log.warn("Stale deploy request rejected (timestamp: {})", payload.timestamp());
            return ResponseEntity.status(401).build();
        }

        if (payload.image() == null || payload.image().isBlank()
                || payload.appName() == null || payload.appName().isBlank()) {
            log.warn("Deploy-hook rejected: missing image or app_name (image='{}', app_name='{}')",
                    payload.image(), payload.appName());
            return ResponseEntity.badRequest().build();
        }

        if (!VALID_APP_NAME.matcher(payload.appName()).matches()) {
            log.warn("Invalid app name rejected: {}", payload.appName());
            return ResponseEntity.badRequest().build();
        }

        CreateDeploymentRequest req = toCreateRequest(payload, triggerHeader);

        Optional<ActionLockService.LockHandle> maybeLock = actionLockService.tryLock("app:" + payload.appName());
        if (maybeLock.isEmpty()) {
            log.warn("Deploy lock held for {}, returning 409", payload.appName());
            return ResponseEntity.status(409).body(Map.of("error", "Deployment already in progress for " + payload.appName()));
        }

        ActionLockService.LockHandle lock = maybeLock.get();
        try {
            deploymentService.handleWebhookDeployAsync(req, lock);
        } catch (TaskRejectedException e) {
            // Async submission failed synchronously (executor saturated / shutting down).
            // The @Async method never runs, so its finally-close never fires — release here
            // or the per-app semaphore is leaked permanently.
            lock.close();
            log.warn("Deploy executor rejected task for {}, returning 503", payload.appName(), e);
            return ResponseEntity.status(503).body(Map.of("error", "deploy queue saturated, retry later"));
        }
        return ResponseEntity.status(202).body(Map.of("status", "accepted", "appName", payload.appName()));
    }

    private CreateDeploymentRequest toCreateRequest(WebhookDeployPayload payload, String triggerHeader) {
        int containerPort = payload.port() != null ? payload.port() : defaultContainerPort;
        LocalDateTime commitTs = payload.commitTimestamp() != null
                ? LocalDateTime.ofInstant(Instant.ofEpochSecond(payload.commitTimestamp()), ZoneOffset.UTC)
                : null;
        TriggerSource trigger = "manual".equalsIgnoreCase(triggerHeader)
                ? TriggerSource.MANUAL
                : TriggerSource.AUTOMATIC;
        return new CreateDeploymentRequest(
                payload.appName(), payload.repoUrl(), payload.image(), containerPort,
                payload.branch(), payload.commitSha(), payload.commitMessage(),
                payload.commitAuthor(), commitTs, null, trigger);
    }
}
