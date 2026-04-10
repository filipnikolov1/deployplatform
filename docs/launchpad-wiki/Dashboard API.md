# Dashboard API

REST API for managing deployments — list, inspect, stop, and restart apps.

## Source

`DeploymentController.java` in `deployment/controller/`

## Endpoints

All under `/api/apps` — protected by [[Security]] API key auth (header: `X-API-Key`).

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/apps` | List all deployments |
| GET | `/api/apps/{appName}` | Get single deployment |
| POST | `/api/apps/{appName}/restart` | Restart (re-pull + recreate) |
| POST | `/api/apps/{appName}/stop` | Stop container |

Also includes [[Environment Variables]] endpoints and [[Build Log Streaming]] endpoint.

## Response Format

Returns `Deployment` JSON objects:

```json
{
  "id": 1,
  "appName": "test",
  "repoUrl": "https://github.com/filipnikolov1/test",
  "imageName": "filipn123/test:latest",
  "status": "RUNNING",
  "containerPort": 3000,
  "createdAt": "2026-03-28T...",
  "updatedAt": "2026-03-28T..."
}
```

## Authentication

All `/api/**` endpoints (except build logs) require the `X-API-Key` header matching `APP_API_KEY` from config. See [[Security]] for details.

See also: [[Deployment Service]], [[API Reference]], [[Security]]

#feature #api