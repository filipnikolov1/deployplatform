CREATE TABLE user_account (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    api_key_hash  VARCHAR(255) NOT NULL,
    preferences   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMP    NOT NULL
);
