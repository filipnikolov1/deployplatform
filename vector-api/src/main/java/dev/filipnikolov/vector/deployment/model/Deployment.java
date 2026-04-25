package dev.filipnikolov.vector.deployment.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @Column(length = 63)
    private String subdomain;

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

    private LocalDateTime deletedAt;

    @Column(length = 500)
    private String pinnedImage;

    private LocalDateTime pinnedAt;

    @Column(name = "is_self_app", nullable = false)
    private boolean selfApp = false;

    @JsonProperty("isSelfApp")
    public boolean isSelfApp() {
        return selfApp;
    }

    @Column(name = "latest_known_image")
    private String latestKnownImage;

    @Column(name = "latest_known_sha")
    private String latestKnownSha;

    @Column(name = "latest_known_message", columnDefinition = "TEXT")
    private String latestKnownMessage;

}
