# Build Log Streaming

Real-time deployment log streaming via Server-Sent Events (SSE).

## Source

- `BuildLogController.java` in `docker/buildlog/controller/`
- `BuildLogServiceImpl.java` in `docker/buildlog/service/impl/`

## How It Works

1. Client opens SSE connection: `GET /api/apps/{appName}/logs/build`
2. `BuildLogService` registers an `SseEmitter` for the appName
3. During [[Docker Service]] `pullAndRun()`, progress events are sent via `buildLogService.send()`
4. Events include: pull progress, container creation, start, routing info
5. Stream completes when deployment finishes via `buildLogService.complete()`

## Endpoint

`GET /api/apps/{appName}/logs/build` — **publicly accessible** (no API key required, configured in [[Security]])

This allows build UIs or dashboards to subscribe without needing API credentials.

## Event Types

```
data: Pulling image: filipn123/test:latest
data: Downloading [=====>    ] 45%
data: Pull complete
data: Stopping existing container...
data: Starting container: abc123def456
data: Container running at test.localhost
```

## Integration

The [[Docker Service]] calls `buildLogService.send()` and `buildLogService.complete()` throughout the `pullAndRun` flow. Subscribe to the SSE endpoint *before* triggering a deploy to capture all events.

See also: [[Docker Service]], [[API Reference]], [[Dashboard API]]

#feature