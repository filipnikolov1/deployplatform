CREATE TABLE analyzer.timeline_event (
    id              BIGSERIAL PRIMARY KEY,
    app_name        VARCHAR(100) NOT NULL,
    event_type      VARCHAR(50)  NOT NULL,
    commit_sha      VARCHAR(40),
    occurred_at     TIMESTAMP    NOT NULL,
    metadata_json   TEXT,
    source_event_id BIGINT
);

CREATE INDEX idx_timeline_app_time ON analyzer.timeline_event (app_name, occurred_at DESC);
CREATE INDEX idx_timeline_occurred  ON analyzer.timeline_event (occurred_at DESC);

-- Prevents duplicate rows from the same deployment_event during backfill / catch-up
CREATE UNIQUE INDEX idx_timeline_source_uniq ON analyzer.timeline_event (source_event_id)
    WHERE source_event_id IS NOT NULL;
