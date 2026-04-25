package dev.filipnikolov.vector.setup.controller;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @Value("${deploy.hook.secret:}")
    private String deployHookSecret;

    @Value("${github.token:}")
    private String githubToken;

    private final DockerClient dockerClient;

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
}
