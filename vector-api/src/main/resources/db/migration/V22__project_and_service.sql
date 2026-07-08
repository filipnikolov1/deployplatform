CREATE TABLE project (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    repo_full_name VARCHAR(200) NOT NULL,
    installation_id BIGINT,
    default_branch VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE TABLE project_service (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    module_path VARCHAR(300) NOT NULL,
    stack VARCHAR(20) NOT NULL,
    build_mode VARCHAR(16) NOT NULL,
    exposed BOOLEAN NOT NULL DEFAULT TRUE,   -- false = background worker: no subdomain/route/SERVICE_* URL
    app_name VARCHAR(100) NOT NULL UNIQUE,   -- FK-by-convention to deployment.app_name
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);
CREATE TABLE project_env_var (               -- C12: shared vars inherited by all services
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    env_key VARCHAR(200) NOT NULL,
    env_value_enc TEXT NOT NULL,             -- same cipher as env_var
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (project_id, env_key)
);

-- Task 12: connect-flow registration needs to distinguish app origin and a PROVISIONING
-- status distinct from PENDING (deploy-hook's pre-first-run state); not part of the plan's
-- pinned V22 SQL block, added here per the plan's own fallback instruction.
ALTER TABLE deployment ADD COLUMN deploy_source VARCHAR(20) NOT NULL DEFAULT 'WEBHOOK';
