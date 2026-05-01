-- Crash analyses (one per crash event; deterministic data populated on CRASHED notification,
-- ai_narration filled in by Phase 6).
CREATE TABLE analyzer.crash_analysis (
    id                   BIGSERIAL PRIMARY KEY,
    app_name             VARCHAR(100) NOT NULL,
    crash_event_id       BIGINT NOT NULL,
    suspect_commit_sha   TEXT,
    last_good_commit_sha TEXT,
    suspect_file_path    TEXT,
    suspect_line         INTEGER,
    ai_narration         TEXT,
    ai_provider_used     VARCHAR(50),
    evidence_json        TEXT NOT NULL,
    signals_json         TEXT NOT NULL,
    generated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (crash_event_id)
);

CREATE INDEX idx_crash_analysis_app ON analyzer.crash_analysis (app_name, generated_at DESC);
