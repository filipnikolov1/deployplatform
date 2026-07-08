-- Task 19: explicit exposed plumbing to container creation (2026-07-06 amendment) — mirrors
-- project_service.exposed (V22) so single-module apps (no project_service row) and self-apps
-- also carry the flag through to the deploy path.
ALTER TABLE deployment ADD COLUMN exposed BOOLEAN NOT NULL DEFAULT TRUE;

-- Per-app pending build state for the C14 event-driven pipeline: push primes this row,
-- workflow_run/workflow_job events update it, workflow_run completed consumes+clears it.
-- Survives restarts so the ordering guard (stale-sha check) and self-app branch work after
-- a missed event / Vector restart.
CREATE TABLE pending_build (
    app_name        VARCHAR(100) PRIMARY KEY,
    operation_id    VARCHAR(36) NOT NULL,
    head_sha        VARCHAR(64) NOT NULL,
    image_target    VARCHAR(500) NOT NULL,
    branch          VARCHAR(255),
    commit_message  TEXT,
    commit_author   VARCHAR(255),
    run_id          BIGINT,
    run_html_url    VARCHAR(500),
    status          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);
