package dev.filipnikolov.vector.githubapp.repository;

import dev.filipnikolov.vector.githubapp.model.GitHubInstallation;
import dev.filipnikolov.vector.githubapp.model.InstallationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GitHubInstallationRepository extends JpaRepository<GitHubInstallation, Long> {

    Optional<GitHubInstallation> findByInstallationId(Long installationId);

    List<GitHubInstallation> findByStatus(InstallationStatus status);
}
