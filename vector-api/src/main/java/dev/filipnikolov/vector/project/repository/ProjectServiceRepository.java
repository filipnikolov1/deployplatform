package dev.filipnikolov.vector.project.repository;

import dev.filipnikolov.vector.project.model.ProjectService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectServiceRepository extends JpaRepository<ProjectService, Long> {

    List<ProjectService> findByProjectId(Long projectId);

    Optional<ProjectService> findByAppName(String appName);
}
