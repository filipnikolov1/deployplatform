ALTER TABLE deployment
    ADD COLUMN pinned_image VARCHAR(500),
    ADD COLUMN pinned_at    TIMESTAMP;
