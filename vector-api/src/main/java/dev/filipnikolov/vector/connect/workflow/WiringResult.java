package dev.filipnikolov.vector.connect.workflow;

import dev.filipnikolov.vector.githubapp.model.WorkflowMode;

public record WiringResult(WorkflowMode mode, String filePath, String snippet) {}
