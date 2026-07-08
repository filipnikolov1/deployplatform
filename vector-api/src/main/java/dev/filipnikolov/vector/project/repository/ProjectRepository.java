package dev.filipnikolov.vector.project.repository;

import dev.filipnikolov.vector.project.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByRepoFullName(String repoFullName);
}
