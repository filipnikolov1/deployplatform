package dev.filipnikolov.vector.deployhook.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_idempotency")
public class WebhookIdempotencyRecord {

    @Id
    @Column(name = "signature", length = 80)
    private String signature;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    protected WebhookIdempotencyRecord() {
    }

    public WebhookIdempotencyRecord(String signature, LocalDateTime receivedAt) {
        this.signature = signature;
        this.receivedAt = receivedAt;
    }

    public String getSignature() {
        return signature;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}
