package dev.filipnikolov.vector.analyzer.timeline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {

    List<TimelineEvent> findByAppNameAndOccurredAtBetweenOrderByOccurredAtDesc(
            String appName, LocalDateTime from, LocalDateTime to);

    @Query("SELECT MAX(e.sourceEventId) FROM TimelineEvent e WHERE e.sourceEventId IS NOT NULL")
    Optional<Long> findMaxSourceEventId();

    boolean existsByAppNameAndEventTypeAndCommitSha(String appName, String eventType, String commitSha);

    @Query("SELECT e.eventType, COUNT(e) FROM TimelineEvent e " +
           "WHERE e.appName = :appName AND e.occurredAt >= :since GROUP BY e.eventType")
    List<Object[]> countEventsByType(@Param("appName") String appName, @Param("since") LocalDateTime since);

    @Query("SELECT MAX(e.occurredAt) FROM TimelineEvent e WHERE e.appName = :appName AND e.eventType = 'DEPLOY'")
    Optional<LocalDateTime> findLastDeployTime(@Param("appName") String appName);

    List<TimelineEvent> findTop10ByAppNameAndEventTypeOrderByOccurredAtDesc(String appName, String eventType);
}
