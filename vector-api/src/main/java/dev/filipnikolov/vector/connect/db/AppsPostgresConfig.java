package dev.filipnikolov.vector.connect.db;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "apps_postgres_config")
@Data
public class AppsPostgresConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_password_enc", nullable = false, columnDefinition = "TEXT")
    private String adminPasswordEnc;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
