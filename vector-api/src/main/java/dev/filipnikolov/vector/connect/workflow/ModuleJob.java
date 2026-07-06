package dev.filipnikolov.vector.connect.workflow;

import dev.filipnikolov.vector.connect.detect.BuildMode;

public record ModuleJob(String appName, String modulePath, BuildMode mode, String imageTarget) {}
