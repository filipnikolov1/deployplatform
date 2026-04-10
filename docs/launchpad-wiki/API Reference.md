# API Reference

All HTTP endpoints exposed by [[Launchpad]].

## Webhook Endpoints (public, HMAC-signed)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/webhook/deploy` | [[Webhook Auth]] HMAC | Trigger deployment |

## Dashboard Endpoints (API key required)

Header: `X-API-Key: {APP_API_KEY}` — see [[Security]].

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/apps` | List all deployments |
| GET | `/api/apps/{appName}` | Get deployment details |
| POST | `/api/apps/{appName}/restart` | Restart app |
| POST | `/api/apps/{appName}/stop` | Stop app |

## Environment Variable Endpoints (API key required)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/apps/{appName}/env` | List env vars (decrypted) |
| PUT | `/api/apps/{appName}/env/{key}` | Set env var |
| DELETE | `/api/apps/{appName}/env/{key}` | Delete env var |

## Build Log Endpoint (public)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/apps/{appName}/logs/build` | SSE build log stream |

## Rate Limits

- `/webhook/**`: 30 requests/min per IP
- `/api/**`: 60 requests/min per IP

See also: [[Dashboard API]], [[Webhook Receiver]], [[Environment Variables]], [[Build Log Streaming]], [[Security]]

#api