package dev.filipnikolov.vector.githubapp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filipnikolov.vector.config.DomainConfig;
import dev.filipnikolov.vector.githubapp.service.GitHubAppConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GitHubAppSetupController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GitHubAppConfigService configService;
    private final DomainConfig domainConfig;
    private final RestClient restClient;

    public GitHubAppSetupController(GitHubAppConfigService configService,
                                     DomainConfig domainConfig,
                                     RestClient.Builder restClientBuilder) {
        this.configService = configService;
        this.domainConfig = domainConfig;
        this.restClient = restClientBuilder.build();
    }

    @GetMapping("/app-manifest")
    public ResponseEntity<?> manifest() {
        String state = randomHex(16);
        String name = "deploy-platform-" + randomHex(8);
        String dashboardBaseUrl = domainConfig.dashboardBaseUrl();

        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("contents", "write");
        permissions.put("workflows", "write");
        permissions.put("secrets", "write");
        permissions.put("actions", "write");
        permissions.put("metadata", "read");
        permissions.put("pull_requests", "read");

        Map<String, Object> hookAttributes = new LinkedHashMap<>();
        hookAttributes.put("url", domainConfig.publicBaseUrl() + "/api/github/app-webhook");

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", name);
        manifest.put("url", dashboardBaseUrl);
        manifest.put("hook_attributes", hookAttributes);
        manifest.put("redirect_url", dashboardBaseUrl + "/setup/github-app/callback");
        manifest.put("public", true);
        manifest.put("default_permissions", permissions);
        manifest.put("default_events", List.of("push", "pull_request", "workflow_run", "workflow_job"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("postUrl", "https://github.com/settings/apps/new?state=" + state);
        response.put("manifest", manifest);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/app-manifest/exchange")
    public ResponseEntity<?> exchange(@RequestBody Map<String, String> body) {
        if (configService.resolve().isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "GitHub App already configured"));
        }

        String code = body.get("code");
        String json = restClient.post()
                .uri("https://api.github.com/app-manifests/" + code + "/conversions")
                .retrieve()
                .body(String.class);

        JsonNode node;
        try {
            node = MAPPER.readTree(json != null ? json : "{}");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse app-manifest conversion response", e);
        }

        String appId = node.path("id").asText();
        String slug = node.path("slug").asText();
        String ownerLogin = node.path("owner").path("login").asText();
        String pem = node.path("pem").asText();
        String webhookSecret = node.path("webhook_secret").asText();

        configService.store(appId, slug, ownerLogin, pem, webhookSecret);

        return ResponseEntity.ok(Map.of("slug", slug, "ownerLogin", ownerLogin));
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
