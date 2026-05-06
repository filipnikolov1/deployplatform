CREATE TABLE webhook_idempotency (
    signature VARCHAR(80) PRIMARY KEY,
    received_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_webhook_idempotency_received_at ON webhook_idempotency (received_at);
