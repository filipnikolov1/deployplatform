CREATE TABLE provisioned_database (
    id BIGSERIAL PRIMARY KEY,
    app_name VARCHAR(100) NOT NULL UNIQUE,
    db_name VARCHAR(63) NOT NULL,
    db_user VARCHAR(63) NOT NULL,
    db_pass_enc TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    orphaned_at TIMESTAMP NULL
);

-- Single-row config holding the generated vector-apps-postgres admin (superuser) password,
-- same cipher + single-row pattern as github_app_config (Task 4).
CREATE TABLE apps_postgres_config (
    id BIGSERIAL PRIMARY KEY,
    admin_password_enc TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
