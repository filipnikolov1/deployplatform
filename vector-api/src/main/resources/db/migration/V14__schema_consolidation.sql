-- Widen deployment.commit_message from VARCHAR(500) to TEXT
ALTER TABLE deployment ALTER COLUMN commit_message TYPE TEXT;

-- Add last_deployed_at with backfill from most recent successful deploy event
ALTER TABLE deployment ADD COLUMN last_deployed_at TIMESTAMP;
UPDATE deployment d
SET last_deployed_at = (
    SELECT MAX(e.created_at)
    FROM deployment_event e
    WHERE e.app_name = d.app_name
      AND e.event_type = 'DEPLOY_FINISHED'
      AND e.status = 'SUCCESS'
);

-- Add build_duration_ms with backfill from most recent successful deploy event
ALTER TABLE deployment ADD COLUMN build_duration_ms BIGINT;
UPDATE deployment d
SET build_duration_ms = (
    SELECT e.duration_ms
    FROM deployment_event e
    WHERE e.app_name = d.app_name
      AND e.event_type = 'DEPLOY_FINISHED'
      AND e.status = 'SUCCESS'
    ORDER BY e.created_at DESC
    LIMIT 1
);

-- Add rollback_from_sha to deployment_event
ALTER TABLE deployment_event ADD COLUMN rollback_from_sha VARCHAR(64);

-- Drop api_key_hash from user_account (keep the table)
ALTER TABLE user_account DROP COLUMN api_key_hash;

-- Make deployment_event.app_name nullable to support ON DELETE SET NULL
-- (must happen before the orphan-cleanup UPDATE below)
ALTER TABLE deployment_event ALTER COLUMN app_name DROP NOT NULL;

-- Pre-FK cleanup: null out deployment_event rows whose app_name no longer exists
UPDATE deployment_event
SET app_name = NULL
WHERE app_name IS NOT NULL
  AND app_name NOT IN (SELECT app_name FROM deployment);

-- Pre-FK cleanup: remove env_var rows whose app_name no longer exists
DELETE FROM env_var
WHERE app_name NOT IN (SELECT app_name FROM deployment);

-- Pre-FK cleanup: remove pending_self_update rows whose app_name no longer exists
DELETE FROM pending_self_update
WHERE app_name NOT IN (SELECT app_name FROM deployment);

-- FK: deployment_event -> deployment (preserve audit rows with NULL on delete)
ALTER TABLE deployment_event
    ADD CONSTRAINT fk_event_deployment
    FOREIGN KEY (app_name) REFERENCES deployment(app_name) ON DELETE SET NULL;

-- FK: env_var -> deployment (cascade delete env vars with the app)
ALTER TABLE env_var
    ADD CONSTRAINT fk_envvar_deployment
    FOREIGN KEY (app_name) REFERENCES deployment(app_name) ON DELETE CASCADE;

-- FK: pending_self_update -> deployment (cascade delete pending updates with the app)
ALTER TABLE pending_self_update
    ADD CONSTRAINT fk_pending_deployment
    FOREIGN KEY (app_name) REFERENCES deployment(app_name) ON DELETE CASCADE;

-- Index for last_deployed_at sorting
CREATE INDEX idx_deployment_last_deployed ON deployment (last_deployed_at DESC);
