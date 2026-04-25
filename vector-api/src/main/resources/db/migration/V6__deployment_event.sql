CREATE TABLE deployment_event (
    id              BIGSERIAL PRIMARY KEY,
    app_name        VARCHAR(100) NOT NULL,
    event_type      VARCHAR(32)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    image_name      VARCHAR(500),
    branch          VARCHAR(255),
    commit_sha      VARCHAR(64),
    commit_message  TEXT,
    commit_author   VARCHAR(255),
    duration_ms     BIGINT,
    error_message   TEXT,
    triggered_by    VARCHAR(16),
    created_at      TIMESTAMP    NOT NULL,
    finished_at     TIMESTAMP
);
CREATE INDEX idx_app_created ON deployment_event (app_name, created_at DESC);
CREATE INDEX idx_created     ON deployment_event (created_at DESC);
