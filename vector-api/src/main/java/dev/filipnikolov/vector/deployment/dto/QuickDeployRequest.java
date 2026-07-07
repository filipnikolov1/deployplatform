package dev.filipnikolov.vector.deployment.dto;

import java.util.Map;

public record QuickDeployRequest(
        String image,
        String appName,
        Integer port,
        Map<String, String> env,
        String subdomain
) {}
