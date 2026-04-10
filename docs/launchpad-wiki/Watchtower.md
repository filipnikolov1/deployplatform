# Watchtower

Auto-update service for [[Launchpad]]'s own container.

## How It Works

Watchtower monitors the `launchpad-launchpad-1` container and polls DockerHub every **300 seconds** (5 minutes). When a new image is detected, it:

1. Pulls the new image
2. Stops the running container
3. Recreates with the same config
4. Starts the new container

## Configuration

In `docker-compose.yml`:

```yaml
watchtower:
  image: containrrr/watchtower
  volumes:
    - /var/run/docker.sock:/var/run/docker.sock
  environment:
    WATCHTOWER_POLL_INTERVAL: 300
  command: launchpad-launchpad-1
```

Only watches the Launchpad container (not deployed apps or Traefik/Postgres).

## Integration with CI/CD

Part of the [[CI/CD Pipeline]] flow:
```
Push to dev -> GitHub Actions -> DockerHub -> Watchtower auto-pulls -> Launchpad updated
```

See also: [[CI/CD Pipeline]], [[Docker]], [[Dockerization]]

#infrastructure