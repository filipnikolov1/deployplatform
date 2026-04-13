package com.filipnikolov.launchpad.deployhook.controller;

import com.filipnikolov.launchpad.deployment.dto.CreateDeploymentRequest;
import com.filipnikolov.launchpad.deployment.model.TriggerSource;
import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import com.filipnikolov.launchpad.deployhook.auth.service.DeployHookAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/deploy-hook")
@RequiredArgsConstructor
public class DeployHookController {

    private static final Logger log = LoggerFactory.getLogger(DeployHookController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern VALID_APP_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$");
    private static final long REPLAY_WINDOW_MS = 5 * 60 * 1000;

    private final DeploymentService deploymentService;
    private final DeployHookAuthService deployHookAuthService;

    @Value("${app.default-port:3000}")
    private int defaultContainerPort;

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON payload", e);
        }
    }

    private boolean isValidAppName(String appName) {
        return appName != null && VALID_APP_NAME.matcher(appName).matches();
    }

    @PostMapping
    public ResponseEntity<Void> handleDeploy(
            @RequestHeader("X-Signature-256") String signature,
            @RequestHeader(value = "X-Launchpad-Trigger", required = false) String triggerHeader,
            @RequestBody String rawBody) {

        if (!deployHookAuthService.isValidSignature(rawBody, signature)) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> payload = parsePayload(rawBody);

        if (!payload.containsKey("timestamp")) {
            return ResponseEntity.badRequest().build();
        }
        long timestamp = ((Number) payload.get("timestamp")).longValue();
        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > REPLAY_WINDOW_MS) {
            log.warn("Stale deploy request rejected (timestamp: {})", timestamp);
            return ResponseEntity.status(401).build();
        }

        String imageName = (String) payload.get("image");
        String appName = (String) payload.get("app_name");
        String repoUrl = (String) payload.get("repo_url");

        if (imageName == null || appName == null) {
            return ResponseEntity.badRequest().build();
        }

        if (!isValidAppName(appName)) {
            log.warn("Invalid app name rejected: {}", appName);
            return ResponseEntity.badRequest().build();
        }

        int containerPort = payload.containsKey("port")
                ? ((Number) payload.get("port")).intValue()
                : defaultContainerPort;

        String branch = (String) payload.get("branch");
        String commitSha = (String) payload.get("commit_sha");
        String commitMessage = (String) payload.get("commit_message");
        String commitAuthor = (String) payload.get("commit_author");
        LocalDateTime commitTs = payload.containsKey("commit_timestamp") && payload.get("commit_timestamp") != null
                ? LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(((Number) payload.get("commit_timestamp")).longValue()),
                    ZoneOffset.UTC)
                : null;

        TriggerSource trigger = "manual".equalsIgnoreCase(triggerHeader)
                ? TriggerSource.MANUAL
                : TriggerSource.AUTOMATIC;

        CreateDeploymentRequest req = new CreateDeploymentRequest(
                appName, repoUrl, imageName, containerPort,
                branch, commitSha, commitMessage, commitAuthor, commitTs, trigger);

        deploymentService.createDeployment(req);
        return ResponseEntity.ok().build();
    }
}
