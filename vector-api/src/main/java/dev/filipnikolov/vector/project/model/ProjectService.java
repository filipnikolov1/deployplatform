package dev.filipnikolov.vector.project.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_service")
@Data
public class ProjectService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "module_path", nullable = false, length = 300)
    private String modulePath;

    @Column(name = "stack", nullable = false, length = 20)
    private String stack;

    @Column(name = "build_mode", nullable = false, length = 16)
    private String buildMode;

    @Column(name = "exposed", nullable = false)
    private boolean exposed = true;

    @Column(name = "app_name", nullable = false, unique = true, length = 100)
    private String appName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
