CREATE TABLE env_var (
    id              BIGSERIAL PRIMARY KEY,
    app_name        VARCHAR(100) NOT NULL,
    var_key         VARCHAR(255) NOT NULL,
    encrypted_value TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (app_name, var_key)
);
