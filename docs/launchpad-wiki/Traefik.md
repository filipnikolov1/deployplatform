# Traefik

Reverse proxy handling subdomain routing for [[Launchpad]] and all deployed apps.

## Version

Traefik v3.3

## How It Works

1. Traefik watches the Docker socket for containers with `traefik.enable=true` label
2. Each container's labels define routing rules
3. Traffic to `appname.domain` is automatically forwarded to the correct container and port

## Launchpad's Own Routing

```
launchpad.localhost      -> Launchpad (port 8082)
/webhook/**              -> Launchpad (PathPrefix rule)
```

## Deployed App Routing

The [[Docker Service]] sets these labels on each deployed container:

```
traefik.enable=true
traefik.http.routers.{app}.rule=Host(`{app}.{domain}`)
traefik.http.routers.{app}.entrypoints=web
traefik.http.services.{app}.loadbalancer.server.port={port}
```

## Configuration

Configured via command-line args in `docker-compose.yml`:
- `--providers.docker=true`
- `--providers.docker.exposedbydefault=false` (opt-in via labels)
- `--entrypoints.web.address=:80`

## Security

Dashboard is secured — `--api.insecure` removed and port 8080 not exposed. See [[Security Hardening]].

## HTTPS (TODO)

Currently HTTP only. HTTPS via Let's Encrypt cert resolver needed for [[VPS Deployment]].

See also: [[Docker]], [[Docker Service]], [[Dockerization]], [[Architecture Overview]]

#infrastructure