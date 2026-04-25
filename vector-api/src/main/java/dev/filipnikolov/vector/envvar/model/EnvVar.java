package dev.filipnikolov.vector.envvar.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "env_var")
@Data
public class EnvVar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String appName;

    private String varKey;

    private String encryptedValue;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
