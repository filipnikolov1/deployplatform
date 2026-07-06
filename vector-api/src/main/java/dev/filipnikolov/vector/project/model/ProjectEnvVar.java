package dev.filipnikolov.vector.project.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_env_var")
@Data
public class ProjectEnvVar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "env_key", nullable = false, length = 200)
    private String envKey;

    @Column(name = "env_value_enc", nullable = false, columnDefinition = "TEXT")
    private String envValueEnc;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
