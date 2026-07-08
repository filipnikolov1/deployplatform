package dev.filipnikolov.vector.githubapp.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_installation")
@Data
public class GitHubInstallation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "installation_id", nullable = false, unique = true)
    private Long installationId;

    @Column(name = "account_login", nullable = false, length = 100)
    private String accountLogin;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InstallationStatus status;

    @Column(name = "suspended", nullable = false)
    private boolean suspended;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
