package dev.filipnikolov.vector.project.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "project")
@Data
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "repo_full_name", nullable = false, length = 200)
    private String repoFullName;

    @Column(name = "installation_id")
    private Long installationId;

    @Column(name = "default_branch", length = 100)
    private String defaultBranch;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
