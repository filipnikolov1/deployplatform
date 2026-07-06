package dev.filipnikolov.vector.connect.detect;

import dev.filipnikolov.vector.github.client.dto.StackKind;

import java.util.Set;

public record ModuleCandidate(String path, StackKind stack, BuildMode buildMode, Integer portGuess,
                               double confidence, boolean exposed) {

    private static final Set<String> WORKER_SEGMENTS = Set.of("worker", "workers", "jobs", "cron", "consumer");

    public ModuleCandidate(String path, StackKind stack, BuildMode buildMode, Integer portGuess, double confidence) {
        this(path, stack, buildMode, portGuess, confidence, defaultExposed(path));
    }

    private static boolean defaultExposed(String path) {
        String lastSegment = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return !WORKER_SEGMENTS.contains(lastSegment);
    }
}
