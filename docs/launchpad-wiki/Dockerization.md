# Dockerization

Full containerization of [[Launchpad]] and its dependencies.

## Dockerfile

Multi-stage build using Eclipse Temurin 21:

```
Stage 1 (build): JDK 21 — mvnw package
Stage 2 (run):   JRE 21 — java -jar app.jar
```

Exposes port 8082.

## docker-compose.yml

Defines 4 services:

| Service | Image | Purpose |
|---------|-------|---------|
| [[Traefik]] | `traefik:v3.3` | Reverse proxy, subdomain routing |
| [[PostgreSQL]] | `postgres:16` | Database |
| launchpad | `filipn123/launchpad-backend:latest` | The app |
| [[Watchtower]] | `containrrr/watchtower` | Auto-updates |

## Network

All services share the `traefik` network. Deployed apps are also attached to this network by the [[Docker Service]].

## Docker Socket

Both Launchpad and [[Traefik]] mount the Docker socket:
- Launchpad: read-write (needs to manage containers)
- Traefik: read-only (watches for container labels)

## Dev Compose

`docker-compose.dev.yml` available for local development with different defaults.

See also: [[Architecture Overview]], [[Docker]], [[CI/CD Pipeline]]

#infrastructure #feature