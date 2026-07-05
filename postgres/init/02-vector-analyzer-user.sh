#!/bin/sh
# Creates the vector_analyzer Postgres user with the password from
# VECTOR_ANALYZER_DB_PASS (compose passes the generated value in). Runs once on
# fresh container creation. For existing containers run the DO block manually
# via: docker exec -i <postgres-container> psql -U launchpad launchpad
set -e

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<EOSQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'vector_analyzer') THEN
        CREATE USER vector_analyzer WITH PASSWORD '${VECTOR_ANALYZER_DB_PASS:-vector_analyzer}';
    END IF;
END
\$\$;

GRANT CONNECT ON DATABASE launchpad TO vector_analyzer;
GRANT USAGE ON SCHEMA public TO vector_analyzer;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO vector_analyzer;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO vector_analyzer;
-- vector_analyzer creates its own schema via Flyway:
GRANT CREATE ON DATABASE launchpad TO vector_analyzer;
EOSQL
