package dev.filipnikolov.vector.githubapp.repository;

import dev.filipnikolov.vector.githubapp.model.GitHubAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitHubAppConfigRepository extends JpaRepository<GitHubAppConfig, Long> {
}
