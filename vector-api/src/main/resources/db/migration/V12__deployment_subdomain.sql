ALTER TABLE deployment ADD COLUMN subdomain VARCHAR(63);

CREATE UNIQUE INDEX uq_deployment_subdomain_live
    ON deployment (subdomain)
    WHERE subdomain IS NOT NULL AND deleted_at IS NULL;
