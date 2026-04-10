CREATE TABLE deployment (
    id          BIGSERIAL PRIMARY KEY,
    app_name    VARCHAR(100) NOT NULL,
    repo_url    VARCHAR(255) NOT NULL,
    image_name  VARCHAR(255) NOT NULL,
    status      VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);