package dev.filipnikolov.vector.connect.detect;

import dev.filipnikolov.vector.github.client.dto.StackKind;

import java.util.Set;

public record ModuleCandidate(String path, StackKind stack, BuildMode buildMode, Integer portGuess,
                               double confidence, boolean exposed, boolean workspaceBuild, BuildTool buildTool) {

    private static final Set<String> WORKER_SEGMENTS = Set.of("worker", "workers", "jobs", "cron", "consumer");

    public ModuleCandidate(String path, StackKind stack, BuildMode buildMode, Integer portGuess, double confidence) {
        this(path, stack, buildMode, portGuess, confidence, defaultExposed(path), false, BuildTool.MAVEN);
    }

    public ModuleCandidate(String path, StackKind stack, BuildMode buildMode, Integer portGuess,
                            double confidence, boolean exposed) {
        this(path, stack, buildMode, portGuess, confidence, exposed, false, BuildTool.MAVEN);
    }

    public ModuleCandidate(String path, StackKind stack, BuildMode buildMode, Integer portGuess,
                            double confidence, boolean exposed, boolean workspaceBuild) {
        this(path, stack, buildMode, portGuess, confidence, exposed, workspaceBuild, BuildTool.MAVEN);
    }

    private static boolean defaultExposed(String path) {
        String lastSegment = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return !WORKER_SEGMENTS.contains(lastSegment);
    }
}
