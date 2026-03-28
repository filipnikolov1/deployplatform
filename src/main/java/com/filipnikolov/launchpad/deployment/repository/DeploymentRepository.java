package com.filipnikolov.launchpad.deployment.repository;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import com.filipnikolov.launchpad.deployment.model.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    /**
     * Finds all deployments with the given status.
     */
    List<Deployment> findByStatus(DeploymentStatus status);

    /**
     * Finds a deployment by app name. Each app has at most one deployment.
     */
    Optional<Deployment> findByAppName(String appName);
}
