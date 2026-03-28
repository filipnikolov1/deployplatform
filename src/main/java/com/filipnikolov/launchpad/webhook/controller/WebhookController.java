package com.filipnikolov.launchpad.webhook.controller;

import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import com.filipnikolov.launchpad.docker.DockerService;
import com.filipnikolov.launchpad.webhook.auth.service.WebhookAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final DeploymentService deploymentService;
    private final WebhookAuthService webhookAuthService;
    private final DockerService dockerService;
    private final com.filipnikolov.launchpad.envvar.service.EnvVarService envVarService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON payload");
        }
    }

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

        deploymentService.createDeployment(appName, repoUrl);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/test-docker")
    public ResponseEntity<String> testDocker() {
        try {
            Map<String, String> envVars = envVarService.getEnvVars("test-nginx");
            String containerId = dockerService.pullAndRun("nginx:latest", "test-nginx", 80, envVars);
            return ResponseEntity.ok("Container started: " + containerId);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed: " + e.getMessage());
        }
    }
}
