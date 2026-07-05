package dev.filipnikolov.vector.githubapp.repository;

import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GitHubRepoRepository extends JpaRepository<GitHubRepo, Long> {

    List<GitHubRepo> findByInstallationId(Long installationId);

    Optional<GitHubRepo> findByFullName(String fullName);

    void deleteByInstallationId(Long installationId);
}
