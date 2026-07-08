package dev.filipnikolov.vector.connect.deploy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pending_build")
@Getter
@Setter
public class PendingBuild {

    @Id
    @Column(name = "app_name")
    private String appName;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Column(name = "head_sha", nullable = false, length = 64)
    private String headSha;

    @Column(name = "image_target", nullable = false, length = 500)
    private String imageTarget;

    @Column(name = "branch")
    private String branch;

    @Column(name = "commit_message", columnDefinition = "TEXT")
    private String commitMessage;

    @Column(name = "commit_author")
    private String commitAuthor;

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "run_html_url", length = 500)
    private String runHtmlUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PendingBuildStatus status = PendingBuildStatus.QUEUED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
