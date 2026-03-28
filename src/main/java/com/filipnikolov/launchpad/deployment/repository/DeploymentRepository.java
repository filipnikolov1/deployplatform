package com.filipnikolov.launchpad.deployment.repository;

import com.filipnikolov.launchpad.deployment.model.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
}
