package com.filipnikolov.launchpad.setup.controller;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.service.DeploymentService;
import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private static final Pattern VALID_APP_NAME =
            Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$");

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @Value("${deploy.hook.secret:}")
    private String deployHookSecret;

    @Value("${github.token:}")
    private String githubToken;

    private final DockerClient dockerClient;
    private final DeploymentService deploymentService;

    @GetMapping("/deploy-url")
    public Map<String, String> deployUrl() {
        return Map.of("url", publicBaseUrl + "/deploy-hook");
    }

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        boolean dockerReachable;
        try {
            dockerClient.pingCmd().exec();
            dockerReachable = true;
        } catch (Exception e) {
            dockerReachable = false;
        }
        return Map.of(
                "secretConfigured", deployHookSecret != null && !deployHookSecret.isBlank(),
                "dockerReachable", dockerReachable,
                "githubTokenConfigured", githubToken != null && !githubToken.isBlank()
        );
    }

    @PostMapping("/precreate")
    public Deployment precreate(@RequestBody Map<String, Object> body) {
        String appName = (String) body.get("appName");
        if (appName == null || !VALID_APP_NAME.matcher(appName).matches()) {
            throw new IllegalArgumentException("Invalid app name");
        }
        int port = body.containsKey("port") && body.get("port") != null
                ? ((Number) body.get("port")).intValue()
                : 3000;
        return deploymentService.precreate(appName, port);
    }
}
