package com.filipnikolov.launchpad.deployment.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Generated;

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

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
