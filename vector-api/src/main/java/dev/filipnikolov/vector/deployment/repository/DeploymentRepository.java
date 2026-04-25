package dev.filipnikolov.vector.deployment.repository;

import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.events.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    /**
     * Finds all deployments with the given status (including soft-deleted).
     */
    List<Deployment> findByStatus(DeploymentStatus status);

    /**
     * Finds a deployment by app name. Each app has at most one deployment.
     * Includes soft-deleted rows so restore can find them.
     */
    Optional<Deployment> findByAppName(String appName);

    /**
     * Returns only live (non-soft-deleted) deployments.
     */
    List<Deployment> findAllByDeletedAtIsNull();

    /**
     * Filters by status and excludes soft-deleted rows.
     */
    List<Deployment> findByStatusAndDeletedAtIsNull(DeploymentStatus status);

    /**
     * Looks up a live deployment by app name (excludes soft-deleted rows).
     */
    Optional<Deployment> findByAppNameAndDeletedAtIsNull(String appName);

    /**
     * Looks up a live deployment by subdomain (excludes soft-deleted rows).
     */
    Optional<Deployment> findBySubdomainAndDeletedAtIsNull(String subdomain);

    List<Deployment> findByDeletedAtIsNotNullAndDeletedAtBefore(LocalDateTime cutoff);
}
