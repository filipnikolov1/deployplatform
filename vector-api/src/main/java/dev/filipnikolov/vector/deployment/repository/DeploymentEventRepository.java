package dev.filipnikolov.vector.deployment.repository;

import dev.filipnikolov.vector.deployment.model.DeploymentEvent;
import dev.filipnikolov.vector.events.DeploymentEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentEventRepository extends JpaRepository<DeploymentEvent, Long> {

    List<DeploymentEvent> findByAppNameOrderByCreatedAtDesc(String appName, Pageable pageable);

    List<DeploymentEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<DeploymentEvent> findTopByAppNameAndEventTypeOrderByCreatedAtDesc(
            String appName, DeploymentEventType eventType);
}
