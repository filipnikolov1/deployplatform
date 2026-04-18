package com.filipnikolov.launchpad.selfapp.repository;

import com.filipnikolov.launchpad.selfapp.model.PendingSelfUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingSelfUpdateRepository extends JpaRepository<PendingSelfUpdate, UUID> {

    List<PendingSelfUpdate> findByAppName(String appName);

    Optional<PendingSelfUpdate> findFirstByAppNameOrderByTriggeredAtDesc(String appName);

    void deleteByAppNameAndTargetSha(String appName, String targetSha);

    List<PendingSelfUpdate> findByTriggeredAtBefore(LocalDateTime cutoff);
}
