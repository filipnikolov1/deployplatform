ALTER TABLE deployment_event
    ADD COLUMN operation_id VARCHAR(36);

CREATE INDEX idx_deployment_event_operation_id
    ON deployment_event (operation_id)
    WHERE operation_id IS NOT NULL;
