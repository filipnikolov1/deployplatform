package dev.filipnikolov.vector.connect.db;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "provisioned_database")
@Data
public class ProvisionedDatabase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_name", nullable = false, unique = true, length = 100)
    private String appName;

    @Column(name = "db_name", nullable = false, length = 63)
    private String dbName;

    @Column(name = "db_user", nullable = false, length = 63)
    private String dbUser;

    @Column(name = "db_pass_enc", nullable = false, columnDefinition = "TEXT")
    private String dbPassEnc;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "orphaned_at")
    private LocalDateTime orphanedAt;
}
