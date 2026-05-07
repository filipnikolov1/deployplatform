package dev.filipnikolov.vector.deployment.model;

import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.events.TriggerSource;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "deployment_event", indexes = {
        @Index(name = "idx_app_created", columnList = "appName, createdAt DESC"),
        @Index(name = "idx_created", columnList = "createdAt DESC"),
        @Index(name = "idx_operation_id", columnList = "operationId")
})
@Data
public class DeploymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String appName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeploymentEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeploymentEventStatus status;

    @Column(length = 500)
    private String imageName;

    @Column(length = 255)
    private String branch;

    @Column(length = 64)
    private String commitSha;

    @Column(columnDefinition = "TEXT")
    private String commitMessage;

    @Column(length = 255)
    private String commitAuthor;

    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TriggerSource triggeredBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    @Column(length = 64)
    private String rollbackFromSha;

    @Column(length = 36)
    private String operationId;
}
