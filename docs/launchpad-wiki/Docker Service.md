# Docker Service

Core service that manages container lifecycle. Heart of [[Launchpad]]'s deployment system.

## Source

`DockerServiceImpl.java` in `docker/service/impl/`

## Responsibilities

1. **Pull images** from [[DockerHub Integration]] with authenticated pulls
2. **Create containers** with [[Traefik]] labels for automatic subdomain routing
3. **Start containers** and stream progress via [[Build Log Streaming]]
4. **Stop and remove** containers for redeployment or stopping
5. **Health checks** — `isContainerRunning()` used by [[Uptime Monitoring]]

## pullAndRun Flow

```
1. Send "Pulling image" log via BuildLogService
2. Pull image (with auth if configured)
3. Stream pull progress to SSE subscribers
4. Stop and remove existing container (if any)
5. Create new container with:
   - Traefik labels (host rule, entrypoint, port)
   - Network: traefik
   - Environment variables from EnvVarService
6. Start container
7. Send "Container running at appname.domain" log
8. Complete SSE stream
```

## Container Labels

Each deployed container gets these [[Traefik]] labels:

```
traefik.enable=true
traefik.http.routers.{appName}.rule=Host(`{appName}.{domain}`)
traefik.http.routers.{appName}.entrypoints=web
traefik.http.services.{appName}.loadbalancer.server.port={containerPort}
```

## Docker Client

Uses `docker-java` library with `ZerodepDockerHttpClient` connecting to the Docker socket.

## Configuration

```properties
docker.socket=${DOCKER_SOCKET:unix:///var/run/docker.sock}
traefik.network=traefik
traefik.domain=localhost
```

See also: [[Deployment Service]], [[DockerHub Integration]], [[Build Log Streaming]], [[Docker]]

#architecture #feature