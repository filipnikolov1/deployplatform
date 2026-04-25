ALTER TABLE deployment ADD COLUMN is_self_app BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE deployment ADD COLUMN latest_known_image VARCHAR(500);
ALTER TABLE deployment ADD COLUMN latest_known_sha VARCHAR(100);
ALTER TABLE deployment ADD COLUMN latest_known_message TEXT;

CREATE TABLE pending_self_update (
    update_id     UUID PRIMARY KEY,
    app_name      VARCHAR(100) NOT NULL,
    target_sha    VARCHAR(100) NOT NULL,
    target_image  VARCHAR(500) NOT NULL,
    triggered_at  TIMESTAMP NOT NULL
);

CREATE INDEX idx_pending_self_update_app_name ON pending_self_update(app_name);
