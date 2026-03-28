package com.filipnikolov.launchpad.webhook.controller;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import com.filipnikolov.launchpad.webhook.auth.service.WebhookAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final DeploymentService deploymentService;
    private final WebhookAuthService webhookAuthService;
    private final ObjectMapper objectMapper;

    @Value("${dockerhub.registry:filipnikolov}")
    private String dockerhubRegistry;

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

    /**
     * Receives GitHub webhook events. Verifies the HMAC signature, filters for
     * push events on refs/heads/main, then triggers a deployment for the repo.
     */
    @PostMapping("/github")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestBody String rawBody) {

        if (!webhookAuthService.isValidSignature(rawBody, signature)) {
            return ResponseEntity.status(401).build();
        }

        if (!"push".equals(event)) {
            return ResponseEntity.ok().build();
        }

        Map<String, Object> payload = parsePayload(rawBody);

        String ref = (String) payload.get("ref");
        if (!"refs/heads/main".equals(ref)) {
            return ResponseEntity.ok().build();
        }

        Map<String, Object> repo = (Map<String, Object>) payload.get("repository");
        String repoUrl = (String) repo.get("clone_url");
        String appName = (String) repo.get("name");

        String imageName = dockerhubRegistry + "/" + appName + ":latest";
        deploymentService.createDeployment(appName, repoUrl, imageName, defaultContainerPort);

        return ResponseEntity.ok().build();
    }

    /**
     * Test endpoint that deploys an nginx container via the full deployment pipeline.
     * Creates a deployment record so the uptime monitor can track it.
     */
    @GetMapping("/test-docker")
    public ResponseEntity<String> testDocker() {
        try {
            Deployment deployment = deploymentService.createDeployment("test-nginx", "https://test.com", "nginx:latest", 80);
            return ResponseEntity.ok("Deployment created: " + deployment.getStatus());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed: " + e.getMessage());
        }
    }
}
