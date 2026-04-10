# PostgreSQL

Persistent storage for [[Launchpad]]. Stores deployment records and encrypted environment variables.

## Version

PostgreSQL 16, running as a Docker container.

## Connection

```
URL: jdbc:postgresql://postgres:5432/launchpad
User: ${DB_USERNAME:-launchpad}
Pass: ${DB_PASSWORD:-launchpad}
```

## Schema Management

Uses **Flyway** for versioned migrations (`spring.flyway.enabled=true`, `spring.jpa.hibernate.ddl-auto=validate`).

See [[Database Schema]] for tables and migrations.

## Persistence

Data stored in Docker volume `postgres_data`.

## Health Check

```
pg_isready -U launchpad (every 5s, 5 retries)
```

Launchpad waits for Postgres to be healthy before starting (`depends_on` with `condition: service_healthy`).

## Security

DB password is a hashed secure password (configured via `DB_PASSWORD` in `.env`).

See also: [[Database Schema]], [[Dockerization]], [[Configuration]]

#infrastructure
