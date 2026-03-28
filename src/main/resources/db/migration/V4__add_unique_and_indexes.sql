ALTER TABLE deployment ADD CONSTRAINT uq_deployment_app_name UNIQUE (app_name);

CREATE INDEX idx_deployment_status ON deployment (status);
CREATE INDEX idx_env_var_app_name ON env_var (app_name);
