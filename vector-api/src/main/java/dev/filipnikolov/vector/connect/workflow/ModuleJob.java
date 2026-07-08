package dev.filipnikolov.vector.connect.workflow;

import dev.filipnikolov.vector.connect.detect.BuildMode;
import dev.filipnikolov.vector.connect.detect.BuildTool;
import dev.filipnikolov.vector.github.client.dto.StackKind;

public record ModuleJob(String appName, String modulePath, BuildMode mode, String imageTarget,
                         StackKind stack, boolean workspaceBuild, BuildTool buildTool) {

    public ModuleJob(String appName, String modulePath, BuildMode mode, String imageTarget) {
        this(appName, modulePath, mode, imageTarget, StackKind.custom, false, BuildTool.MAVEN);
    }

    public ModuleJob(String appName, String modulePath, BuildMode mode, String imageTarget,
                      StackKind stack, boolean workspaceBuild) {
        this(appName, modulePath, mode, imageTarget, stack, workspaceBuild, BuildTool.MAVEN);
    }
}
