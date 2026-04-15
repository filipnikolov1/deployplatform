package com.filipnikolov.launchpad.selfapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pending_self_update")
@Getter
@Setter
public class PendingSelfUpdate {

    @Id
    @Column(name = "update_id")
    private UUID updateId;

    @Column(name = "app_name", nullable = false)
    private String appName;

    @Column(name = "target_sha", nullable = false)
    private String targetSha;

    @Column(name = "target_image", nullable = false)
    private String targetImage;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;
}
