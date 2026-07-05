package dev.filipnikolov.vector.setup.controller;

import com.github.dockerjava.api.DockerClient;
import dev.filipnikolov.vector.config.DomainConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    @Value("${deploy.hook.secret:}")
    private String deployHookSecret;

    @Value("${github.token:}")
    private String githubToken;

    private final DockerClient dockerClient;
    private final DomainConfig domainConfig;

    @GetMapping("/deploy-url")
    public Map<String, String> deployUrl() {
        return Map.of("url", domainConfig.publicBaseUrl() + "/deploy-hook");
    }

    /**
     * Surfaces the (auto-generated) deploy-hook secret so the dashboard setup page
     * can show it for manual CI copy. Sits behind the X-API-Key filter like every
     * other /api/** endpoint; the dashboard session gates the proxy route.
     */
    @GetMapping("/deploy-hook-secret")
    public Map<String, String> hookSecret() {
        return Map.of("secret", deployHookSecret == null ? "" : deployHookSecret);
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
