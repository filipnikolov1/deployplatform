package dev.filipnikolov.vector.connect.workflow;

import dev.filipnikolov.vector.connect.detect.BuildMode;
import dev.filipnikolov.vector.github.client.dto.StackKind;

public record ModuleJob(String appName, String modulePath, BuildMode mode, String imageTarget,
                         StackKind stack, boolean workspaceBuild) {

    public ModuleJob(String appName, String modulePath, BuildMode mode, String imageTarget) {
        this(appName, modulePath, mode, imageTarget, StackKind.custom, false);
    }
}
