package dev.filipnikolov.vector.progress;

import java.time.Instant;

public record ProgressFrame(
        String stage,
        String message,
        Long current,
        Long total,
        String unit,
        Instant emittedAt
) {}
