# Deployment Pipeline

The end-to-end flow from code push to running container, connecting [[Launchpad]]'s core systems.

## Full Flow

```
1. Developer pushes to project repo
2. GitHub Actions builds Docker image
3. GitHub Actions pushes image to DockerHub
4. GitHub Actions calls POST /webhook/deploy with signed payload
5. Launchpad verifies signature via [[Webhook Auth]]
6. [[Deployment Service]] creates/updates deployment record in [[PostgreSQL]]
7. [[Docker Service]] pulls image from [[DockerHub Integration]]
8. [[Build Log Streaming]] sends real-time progress via SSE
9. Docker Service creates container with [[Traefik]] labels
10. Container starts, routable at appname.domain.com
11. [[Uptime Monitoring]] begins health checking every 60s
```

## Payload Format

The deploy webhook expects a signed JSON body:

```json
{
  "image": "filipn123/test:latest",
  "app_name": "test",
  "repo_url": "https://github.com/filipnikolov1/test",
  "port": 3000,
  "timestamp": 1711641600000
}
```

Signed with HMAC-SHA256 in the `X-Signature-256` header. See [[Webhook Auth]] for details.

## GitHub Actions Integration

The calling workflow (in the deployed project, not Launchpad) signs and sends the deploy request. See [[CI/CD Pipeline]] for Launchpad's own build pipeline.

## Tested Flow

Successfully tested on 2026-03-28 with `filipnikolov1/test` (simple Express app):
- Push -> GitHub Actions -> DockerHub -> `/webhook/deploy` -> live at `test.localhost`
- Uses Actions-based deploy flow (not raw GitHub webhooks) to avoid race condition where webhook fires before image is pushed

#architecture #feature