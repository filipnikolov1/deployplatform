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
            Boolean exposed,
            Boolean workspaceBuild,
            Map<String, String> env
    ) {
        // Jackson 3 rejects null-into-primitive, so omitted booleans must be normalized
        // here: exposed defaults true (plan semantics), workspaceBuild defaults false.
        public ModuleSelection {
            exposed = exposed == null || exposed;
            workspaceBuild = workspaceBuild != null && workspaceBuild;
        }
    }
}
