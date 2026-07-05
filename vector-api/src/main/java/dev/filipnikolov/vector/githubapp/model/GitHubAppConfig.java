package dev.filipnikolov.vector.githubapp.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_app_config")
@Data
public class GitHubAppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false, length = 32)
    private String appId;

    @Column(name = "app_slug", length = 100)
    private String appSlug;

    @Column(name = "owner_login", length = 100)
    private String ownerLogin;

    @Column(name = "private_key_pem_enc", nullable = false, columnDefinition = "TEXT")
    private String privateKeyPemEnc;

    @Column(name = "webhook_secret_enc", nullable = false, columnDefinition = "TEXT")
    private String webhookSecretEnc;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
