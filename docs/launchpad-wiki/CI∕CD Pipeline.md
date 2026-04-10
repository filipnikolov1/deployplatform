# CI/CD Pipeline

GitHub Actions workflow that builds and pushes [[Launchpad]]'s own Docker image.

## Workflow

File: `.github/workflows/build-and-push.yml`

Triggers on push to `dev` branch:

```
1. Checkout code
2. Login to DockerHub (filipn123)
3. Build multi-stage Dockerfile
4. Push as filipn123/launchpad-backend:latest
```

## Auto-Updates

[[Watchtower]] polls DockerHub every 300 seconds and auto-updates the running Launchpad container when a new image is detected.

## Flow

```
Push to dev -> GitHub Actions -> DockerHub -> Watchtower pulls -> Launchpad updated
```

## Secrets

- `DOCKERHUB_USERNAME` — GitHub Actions secret
- `DOCKERHUB_TOKEN` — GitHub Actions secret (DockerHub PAT)

## Note

This is Launchpad's **own** CI/CD. For deployed apps, the deploy flow uses the [[Deployment Pipeline]] with `/webhook/deploy`.

See also: [[Watchtower]], [[Dockerization]], [[DockerHub Integration]]

#infrastructure