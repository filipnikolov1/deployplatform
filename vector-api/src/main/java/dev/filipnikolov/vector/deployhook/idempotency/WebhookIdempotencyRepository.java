package dev.filipnikolov.vector.deployhook.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface WebhookIdempotencyRepository extends JpaRepository<WebhookIdempotencyRecord, String> {

    void deleteByReceivedAtBefore(LocalDateTime cutoff);
}
