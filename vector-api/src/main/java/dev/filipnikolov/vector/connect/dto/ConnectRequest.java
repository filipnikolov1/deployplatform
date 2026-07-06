package dev.filipnikolov.vector.connect.dto;

import dev.filipnikolov.vector.connect.detect.BuildMode;
import dev.filipnikolov.vector.githubapp.model.WorkflowMode;

import java.util.List;
import java.util.Map;

public record ConnectRequest(
        String repoFullName,
        String branch,
        List<ModuleSelection> modules,
        WorkflowMode workflowMode,
        boolean provisionDb
) {

    public record ModuleSelection(
            String name,
            String path,
            String stack,
            BuildMode buildMode,
            Integer port,
            String subdomain,
            boolean exposed,
            Map<String, String> env
    ) {}
}
