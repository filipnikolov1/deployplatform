ALTER TABLE pending_self_update
    ADD COLUMN operation_id VARCHAR(36);

UPDATE pending_self_update
SET operation_id = update_id::text
WHERE operation_id IS NULL;

ALTER TABLE pending_self_update
    ALTER COLUMN operation_id SET NOT NULL;

CREATE INDEX idx_pending_self_update_operation_id
    ON pending_self_update (operation_id);
