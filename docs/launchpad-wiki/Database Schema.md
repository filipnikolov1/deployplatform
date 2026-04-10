# Database Schema

[[PostgreSQL]] schema managed by Flyway migrations. Two tables: `deployment` and `env_var`.

## Tables

### deployment

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL | Primary key |
| app_name | VARCHAR(100) | Unique, indexed |
| repo_url | VARCHAR(255) | |
| image_name | VARCHAR(255) | |
| status | VARCHAR(50) | PENDING, RUNNING, FAILED, STOPPED, DOWN |
| container_port | INTEGER | Default 3000 |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

Used by [[Deployment Service]] and [[Uptime Monitoring]].

### env_var

| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL | Primary key |
| app_name | VARCHAR(100) | |
| var_key | VARCHAR(255) | |
| encrypted_value | TEXT | AES-256-GCM encrypted |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

Unique constraint on `(app_name, var_key)`. Used by [[Environment Variables]].

## Indexes

- `uq_deployment_app_name` — unique on `deployment.app_name`
- `idx_deployment_status` — on `deployment.status` (for [[Uptime Monitoring]] queries)
- `idx_env_var_app_name` — on `env_var.app_name`

## Migrations

| Version | Description |
|---------|-------------|
| V1 | Create `deployment` table |
| V2 | Create `env_var` table |
| V3 | Add `container_port` column |
| V4 | Add unique constraint and indexes |

Files in `src/main/resources/db/migration/`.

See also: [[PostgreSQL]], [[Deployment Service]], [[Environment Variables]]

#architecture