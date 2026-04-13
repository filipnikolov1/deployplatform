ALTER TABLE deployment ADD COLUMN deleted_at TIMESTAMP;
CREATE INDEX idx_deployment_deleted ON deployment (deleted_at);
