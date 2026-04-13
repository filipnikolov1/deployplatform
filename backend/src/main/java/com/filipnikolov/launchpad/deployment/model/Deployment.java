package com.filipnikolov.launchpad.deployment.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "deployment")
@Data
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String appName;

    private String repoUrl;

    private String imageName;

    private int containerPort;

    @Enumerated(EnumType.STRING)
    private DeploymentStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(length = 255)
    private String branch;

    @Column(length = 64)
    private String commitSha;

    @Column(length = 500)
    private String commitMessage;

    @Column(length = 255)
    private String commitAuthor;

    private LocalDateTime commitTimestamp;

}
