-- Log storage with hybrid retention
CREATE TABLE analyzer.log_entry (
    id             BIGSERIAL PRIMARY KEY,
    app_name       VARCHAR(100) NOT NULL,
    deployment_id  BIGINT NOT NULL,
    commit_sha     TEXT,
    timestamp      TIMESTAMP NOT NULL,
    stream         VARCHAR(8) NOT NULL CHECK (stream IN ('stdout','stderr')),
    line           TEXT NOT NULL
);
CREATE INDEX idx_log_app_time   ON analyzer.log_entry (app_name, timestamp DESC);
CREATE INDEX idx_log_deployment ON analyzer.log_entry (deployment_id);
CREATE INDEX idx_log_commit     ON analyzer.log_entry (commit_sha) WHERE commit_sha IS NOT NULL;

-- GitHub commit metadata cache
CREATE TABLE analyzer.commit_cache (
    repo_full_name VARCHAR(200) NOT NULL,
    sha            TEXT NOT NULL,
    author         VARCHAR(200),
    authored_at    TIMESTAMP,
    message        TEXT,
    PRIMARY KEY (repo_full_name, sha)
);

-- GitHub diff cache (between two SHAs)
CREATE TABLE analyzer.diff_cache (
    repo_full_name VARCHAR(200) NOT NULL,
    base_sha       TEXT NOT NULL,
    head_sha       TEXT NOT NULL,
    diff_json      TEXT NOT NULL,
    cached_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (repo_full_name, base_sha, head_sha)
);
