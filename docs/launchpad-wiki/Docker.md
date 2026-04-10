# Docker

Container runtime underlying [[Launchpad]]. Both the platform itself and deployed apps run as Docker containers.

## docker-java Library

Launchpad uses the `docker-java` library to interact with the Docker daemon via the Docker socket. See [[Docker Service]] for the implementation.

## Docker Socket

Mounted into the Launchpad container at `/var/run/docker.sock`. This gives Launchpad full control over the Docker daemon — it can pull images, create/start/stop/remove containers.

Also mounted (read-only) into [[Traefik]] for automatic service discovery.

## Container Lifecycle

```
Image pull -> Stop old container -> Create new container -> Start -> Running
                                                              |
                                                 Traefik auto-discovers via labels
```

## Related

- [[Docker Service]] — Java service managing containers
- [[DockerHub Integration]] — Authenticated image pulls
- [[Dockerization]] — Launchpad's own Dockerfile and compose setup
- [[Traefik]] — Routes traffic to containers
- [[Watchtower]] — Auto-updates Launchpad container

#infrastructure