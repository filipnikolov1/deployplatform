# Environment Variables

Per-app encrypted environment variable management. Env vars are injected into containers at deploy time.

## Source

- `EnvVarController.java` in `envvar/controller/`
- `EnvVarServiceImpl.java` in `envvar/service/impl/`
- `EncryptionServiceImpl.java` in `envvar/crypto/impl/`

## Encryption

Uses **AES-256-GCM** via the [[Encryption Service]]:
- Values encrypted at rest in [[PostgreSQL]]
- Decrypted only when injecting into containers
- Key configured via `ENCRYPTION_KEY` env var (base64-encoded 32 bytes)

## API Endpoints

All under `/api/apps/{appName}/env` — protected by [[Security]] API key auth.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/apps/{appName}/env` | List all env vars (decrypted) |
| PUT | `/api/apps/{appName}/env/{key}` | Set/update an env var |
| DELETE | `/api/apps/{appName}/env/{key}` | Delete an env var |

## Database

Stored in `env_var` table — see [[Database Schema]].

```sql
env_var (id, app_name, var_key, encrypted_value, created_at, updated_at)
```

Unique constraint on `(app_name, var_key)`.

## How Env Vars Flow

1. User sets env vars via [[Dashboard API]] (PUT endpoint)
2. Values encrypted by [[Encryption Service]] and stored
3. On deploy/restart, [[Deployment Service]] calls `envVarService.getEnvVars(appName)`
4. Values decrypted and passed to [[Docker Service]] as container environment

See also: [[Encryption Service]], [[Dashboard API]], [[Deployment Service]]

#feature #security