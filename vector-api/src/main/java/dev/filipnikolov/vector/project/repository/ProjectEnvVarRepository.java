package dev.filipnikolov.vector.project.repository;

import dev.filipnikolov.vector.project.model.ProjectEnvVar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectEnvVarRepository extends JpaRepository<ProjectEnvVar, Long> {

    Optional<ProjectEnvVar> findByProjectIdAndEnvKey(Long projectId, String envKey);
}
