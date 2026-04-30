CREATE SCHEMA IF NOT EXISTS analyzer;

CREATE TABLE analyzer.analyzer_health (
    id         BIGSERIAL PRIMARY KEY,
    checked_at TIMESTAMP NOT NULL DEFAULT NOW()
);
