package dev.filipnikolov.vector.githubapp.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_repo")
@Data
public class GitHubRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "installation_id", nullable = false)
    private Long installationId;

    @Column(name = "full_name", nullable = false, unique = true, length = 200)
    private String fullName;

    @Column(name = "default_branch", length = 100)
    private String defaultBranch;

    @Column(name = "private", nullable = false)
    private boolean isPrivate;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_mode", length = 16)
    private WorkflowMode workflowMode;

    @Column(name = "workflow_template_version")
    private Integer workflowTemplateVersion;

    @Column(name = "expected_jobs", columnDefinition = "jsonb")
    private String expectedJobs;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
