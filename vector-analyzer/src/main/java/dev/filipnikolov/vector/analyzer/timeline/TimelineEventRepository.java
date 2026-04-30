package dev.filipnikolov.vector.analyzer.timeline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {

    List<TimelineEvent> findByAppNameAndOccurredAtBetweenOrderByOccurredAtDesc(
            String appName, LocalDateTime from, LocalDateTime to);

    @Query("SELECT MAX(e.sourceEventId) FROM TimelineEvent e WHERE e.sourceEventId IS NOT NULL")
    Optional<Long> findMaxSourceEventId();

    boolean existsByAppNameAndEventTypeAndCommitSha(String appName, String eventType, String commitSha);
}
