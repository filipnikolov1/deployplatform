ALTER TABLE deployment
    ADD COLUMN branch            VARCHAR(255),
    ADD COLUMN commit_sha        VARCHAR(64),
    ADD COLUMN commit_message    VARCHAR(500),
    ADD COLUMN commit_author     VARCHAR(255),
    ADD COLUMN commit_timestamp  TIMESTAMP;
