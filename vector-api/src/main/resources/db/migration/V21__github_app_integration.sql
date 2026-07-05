CREATE TABLE github_app_config (
    id BIGSERIAL PRIMARY KEY,               -- single row enforced in service
    app_id VARCHAR(32) NOT NULL,
    app_slug VARCHAR(100),
    owner_login VARCHAR(100),
    private_key_pem_enc TEXT NOT NULL,      -- encrypted with the platform encryption key
    webhook_secret_enc TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE TABLE github_installation (
    id BIGSERIAL PRIMARY KEY,
    installation_id BIGINT NOT NULL UNIQUE,
    account_login VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL,       -- User | Organization
    status VARCHAR(16) NOT NULL,             -- PENDING|APPROVED|REJECTED|REMOVED
    suspended BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    approved_at TIMESTAMP
);
CREATE TABLE github_repo (
    id BIGSERIAL PRIMARY KEY,
    installation_id BIGINT NOT NULL REFERENCES github_installation(installation_id) ON DELETE CASCADE,
    full_name VARCHAR(200) NOT NULL UNIQUE,  -- owner/repo
    default_branch VARCHAR(100),
    private BOOLEAN NOT NULL DEFAULT FALSE,
    workflow_mode VARCHAR(16),               -- NULL until connected; MANAGED|CUSTOM
    workflow_template_version INT,
    expected_jobs JSONB,                     -- C14: [{jobName, appName, steps:[...]}] snapshot from render time (frontend placeholders)
    last_seen_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_github_repo_installation ON github_repo(installation_id);
