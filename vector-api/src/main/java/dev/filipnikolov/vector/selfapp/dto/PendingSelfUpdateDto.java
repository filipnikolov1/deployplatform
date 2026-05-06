package dev.filipnikolov.vector.selfapp.dto;

import dev.filipnikolov.vector.selfapp.model.PendingSelfUpdate;
import dev.filipnikolov.vector.selfapp.model.UpdatePhase;

import java.time.LocalDateTime;
import java.util.UUID;

public record PendingSelfUpdateDto(
        UUID updateId,
        String appName,
        UpdatePhase phase,
        String targetSha,
        String targetImage,
        LocalDateTime triggeredAt,
        String operationId
) {
    public static PendingSelfUpdateDto from(PendingSelfUpdate p) {
        return new PendingSelfUpdateDto(
                p.getUpdateId(),
                p.getAppName(),
                p.getPhase(),
                p.getTargetSha(),
                p.getTargetImage(),
                p.getTriggeredAt(),
                p.getOperationId());
    }
}
