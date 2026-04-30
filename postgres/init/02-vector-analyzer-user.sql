-- Creates the vector_analyzer Postgres user and grants the minimum permissions
-- needed for vector-analyzer to run. Flyway (running as this user) creates the
-- analyzer schema on first startup.
--
-- This script runs once on fresh Postgres container creation. For existing
-- containers run it manually:
--   docker exec -i <postgres-container> psql -U launchpad launchpad < postgres/init/02-vector-analyzer-user.sql

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'vector_analyzer') THEN
        CREATE USER vector_analyzer WITH PASSWORD 'vector_analyzer';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE launchpad TO vector_analyzer;
GRANT USAGE ON SCHEMA public TO vector_analyzer;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO vector_analyzer;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO vector_analyzer;
-- vector_analyzer creates its own schema via Flyway:
GRANT CREATE ON DATABASE launchpad TO vector_analyzer;
