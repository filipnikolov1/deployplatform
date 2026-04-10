# Configuration

All configuration for [[Launchpad]], managed via environment variables and Spring properties.

## .env File

Not committed to git. See `.env.example` for template.

| Variable | Purpose | Default |
|----------|---------|---------|
| `DB_URL` | [[PostgreSQL]] connection | `jdbc:postgresql://postgres:5432/launchpad` |
| `DB_USERNAME` | DB user | `launchpad` |
| `DB_PASSWORD` | DB password | `launchpad` |
| `GITHUB_WEBHOOK_SECRET` | [[Webhook Auth]] HMAC secret | (required) |
| `DOCKER_SOCKET` | Docker API socket URI | `unix:///var/run/docker.sock` |
| `DOCKER_SOCKET_PATH` | Socket path for volume mount | `/var/run/docker.sock` |
| `DOCKERHUB_USERNAME` | [[DockerHub Integration]] user | (empty) |
| `DOCKERHUB_TOKEN` | DockerHub PAT | (empty) |
| `APP_DEFAULT_PORT` | Default container port | `3000` |
| `ENCRYPTION_KEY` | [[Encryption Service]] AES key | (required) |
| `RESEND_API_KEY` | [[Notification Service]] API key | (empty) |
| `RESEND_FROM` | Alert email sender | `Launchpad <onboarding@resend.dev>` |
| `RESEND_TO` | Alert email recipient | (empty) |
| `APP_API_KEY` | [[Security]] API key for dashboard | (empty) |

## application.properties

`src/main/resources/application.properties` — maps env vars to Spring properties. Server port: **8082**.

## Hardcoded Values

| Value | Location | Notes |
|-------|----------|-------|
| `traefik.network=traefik` | application.properties | Docker network name |
| `traefik.domain=localhost` | application.properties | Change for production |
| Rate limits | `RateLimitFilter.java` | 30/min webhook, 60/min API |

See also: [[Security]], [[VPS Deployment]]

#architecture