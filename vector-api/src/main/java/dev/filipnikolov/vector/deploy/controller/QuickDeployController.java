package dev.filipnikolov.vector.deploy.controller;

import dev.filipnikolov.vector.common.lock.ActionLockService;
import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.dto.QuickDeployRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentService;
import dev.filipnikolov.vector.envvar.service.EnvVarService;
import dev.filipnikolov.vector.events.DeploySource;
import dev.filipnikolov.vector.events.DeploymentStatus;
import dev.filipnikolov.vector.events.TriggerSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
public class QuickDeployController {

    private static final Logger log = LoggerFactory.getLogger(QuickDeployController.class);
    private static final Pattern VALID_APP_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$");

    private final DeploymentService deploymentService;
    private final DeploymentRepository deploymentRepository;
    private final EnvVarService envVarService;
    private final ActionLockService actionLockService;

    @Value("${app.default-port:3000}")
    private int defaultContainerPort;

    public QuickDeployController(DeploymentService deploymentService,
                                  DeploymentRepository deploymentRepository,
                                  EnvVarService envVarService,
                                  ActionLockService actionLockService) {
        this.deploymentService = deploymentService;
        this.deploymentRepository = deploymentRepository;
        this.envVarService = envVarService;
        this.actionLockService = actionLockService;
    }

    @PostMapping("/api/deploy/quick")
    public ResponseEntity<?> quickDeploy(@RequestBody QuickDeployRequest req) {
        if (req.image() == null || req.image().isBlank()
                || req.appName() == null || req.appName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "image and appName are required"));
        }

        if (!VALID_APP_NAME.matcher(req.appName()).matches()) {
            log.warn("Invalid app name rejected: {}", req.appName());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid app name"));
        }

        Optional<ActionLockService.LockHandle> maybeLock = actionLockService.tryLock("app:" + req.appName());
        if (maybeLock.isEmpty()) {
            log.warn("Deploy lock held for {}, returning 409", req.appName());
            return ResponseEntity.status(409).body(Map.of("error", "Deployment already in progress for " + req.appName()));
        }

        Deployment deployment = deploymentRepository.findByAppNameAndDeletedAtIsNull(req.appName())
                .orElseGet(() -> {
                    Deployment d = new Deployment();
                    d.setAppName(req.appName());
                    d.setCreatedAt(LocalDateTime.now());
                    return d;
                });
        deployment.setDeploySource(DeploySource.QUICK);
        deployment.setStatus(DeploymentStatus.PENDING);
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(deployment);

        if (req.env() != null) {
            for (Map.Entry<String, String> entry : req.env().entrySet()) {
                envVarService.setEnvVar(req.appName(), entry.getKey(), entry.getValue());
            }
        }

        int containerPort = req.port() != null ? req.port() : defaultContainerPort;
        String operationId = UUID.randomUUID().toString();
        CreateDeploymentRequest createReq = new CreateDeploymentRequest(
                req.appName(), null, req.image(), containerPort,
                null, null, null, null, null, req.subdomain(), TriggerSource.MANUAL, operationId);

        ActionLockService.LockHandle lock = maybeLock.get();
        try {
            deploymentService.handleWebhookDeployAsync(createReq, lock);
        } catch (TaskRejectedException e) {
            // Async submission failed synchronously (executor saturated / shutting down).
            // The @Async method never runs, so its finally-close never fires — release here
            // or the per-app semaphore is leaked permanently.
            lock.close();
            log.warn("Deploy executor rejected task for {}, returning 503", req.appName(), e);
            return ResponseEntity.status(503).body(Map.of("error", "deploy queue saturated, retry later"));
        }

        return ResponseEntity.status(202).body(Map.of("status", "accepted", "operationId", operationId));
    }
}
