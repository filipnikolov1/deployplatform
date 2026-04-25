package com.filipnikolov.launchpad.selfapp.dto;

import com.filipnikolov.launchpad.selfapp.model.PendingSelfUpdate;
import com.filipnikolov.launchpad.selfapp.model.UpdatePhase;

import java.time.LocalDateTime;
import java.util.UUID;

public record PendingSelfUpdateDto(
        UUID updateId,
        String appName,
        UpdatePhase phase,
        String targetSha,
        String targetImage,
        LocalDateTime triggeredAt
) {
    public static PendingSelfUpdateDto from(PendingSelfUpdate p) {
        return new PendingSelfUpdateDto(
                p.getUpdateId(),
                p.getAppName(),
                p.getPhase(),
                p.getTargetSha(),
                p.getTargetImage(),
                p.getTriggeredAt());
    }
}
