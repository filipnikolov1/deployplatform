package com.filipnikolov.launchpad.deployment.repository;

import com.filipnikolov.launchpad.deployment.model.DeploymentEvent;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentEventRepository extends JpaRepository<DeploymentEvent, Long> {

    List<DeploymentEvent> findTop20ByAppNameOrderByCreatedAtDesc(String appName);

    List<DeploymentEvent> findTop50ByOrderByCreatedAtDesc();

    Optional<DeploymentEvent> findTopByAppNameAndEventTypeOrderByCreatedAtDesc(
            String appName, DeploymentEventType eventType);
}
