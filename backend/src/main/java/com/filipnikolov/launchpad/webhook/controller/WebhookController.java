package com.filipnikolov.launchpad.webhook.controller;

import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import com.filipnikolov.launchpad.webhook.auth.service.WebhookAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern VALID_APP_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$");
    private static final long REPLAY_WINDOW_MS = 5 * 60 * 1000;

    private final DeploymentService deploymentService;
    private final WebhookAuthService webhookAuthService;

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

    @PostMapping("/deploy")
    public ResponseEntity<Void> handleDeploy(
            @RequestHeader("X-Signature-256") String signature,
            @RequestBody String rawBody) {

        if (!webhookAuthService.isValidSignature(rawBody, signature)) {
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

        deploymentService.createDeployment(appName, repoUrl, imageName, containerPort);
        return ResponseEntity.ok().build();
    }
}
