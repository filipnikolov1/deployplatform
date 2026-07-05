# Vector

A personal PaaS for deploying Dockerized apps on a single VPS. Vector receives an HMAC-signed deploy request from your CI, pulls the new image from DockerHub, recreates the container behind Traefik on its own subdomain, and surfaces live build logs + deploy status in a small ops dashboard.

It's a portfolio project built as a real tool — you can plug it into your own VPS today and use it to host other side projects.

---

## Architecture at a glance

```
GitHub Actions ──push──▶  DockerHub
      │
      └──HMAC POST──▶  Vector API  ──pull──▶  DockerHub
                            │
                            ├──▶  Docker daemon  (create/start/stop containers)
                            ├──▶  Postgres       (deployments, env vars, users)
                            │        └──pg_notify─▶  deployment_event triggers
                            ├──▶  Ollama         (AI log analysis)
                            ├──▶  Resend         (deploy/crash/recovery email alerts)
                            └──▶  UptimeMonitor  (polls containers; emits CRASHED/RESTARTED)

User's browser ──▶  Traefik  ──▶  Vector dashboard
                        └─▶  Deployed apps at `{app}.{your-domain}`
```

Everything runs as Docker containers managed by a single `docker-compose.yml`. Traefik is the only exposed port on the host (80/443); everything else lives on the internal `traefik` network.

### Services in docker-compose

| Service          | What it does                                                              |
| ---------------- | ------------------------------------------------------------------------- |
| `traefik`        | Reverse proxy; routes host → container by Docker labels                   |
| `postgres`       | Stores deployments, encrypted env vars, users                             |
| `ollama`         | Local LLM for "Ask AI: Analyze Logs" in the dashboard                    |
| `vector-api`     | Spring Boot backend — talks to Docker socket, runs deploys                |
| `vector-web`     | Next.js dashboard — deploy status, logs, env var management               |
| `vector-updater` | Self-update service — upgrades `vector-api` and `vector-web` in-place     |

---

## Project layout

```
vector-platform/
├── vector-api/         # Spring Boot app — deploy hook, Docker integration,
│                       #   encrypted env vars, Ollama client (Maven module)
├── vector-ai/          # Shared AI types (Maven module)
├── vector-events/      # Shared event types (Maven module)
├── vector-web/         # Next.js dashboard
├── vector-updater/     # Go self-update service
├── pom.xml             # Maven parent POM (multi-module)
├── docker-compose.yml  # Full local + prod stack
└── .env.example        # Every env var documented
```

---

## Quick start (local dev on macOS/Linux)

**Prerequisites:**

- Docker (Desktop / OrbStack / Colima)
- JDK 21 if you want to run the backend outside Docker
- Node 20 if you want to run the frontend outside Docker

**1. Clone and copy env:**

```bash
git clone <this-repo> vector
cd vector
cp .env.example .env
```

**2. Fill in `.env`** — see the full reference below. At minimum you need:

- `VECTOR_DOMAIN` — keep `localhost` for local dev
- `VECTOR_DASHBOARD_PASSWORD` — dashboard login password
- `VECTOR_GEMINI_API_KEY` — free key from https://aistudio.google.com/apikey

Everything else (API key, session secret, DB passwords, deploy-hook secret,
encryption key, updater token) is generated on first `./vector up` and appended
to `.env`. Back up `VECTOR_ENCRYPTION_KEY`. macOS users may also set
`VECTOR_DOCKER_SOCKET_PATH` (OrbStack: `~/.orbstack/run/docker.sock`).

**3. Bring it up:**

```bash
./vector up -d
```

**4. Sanity check:**

```bash
curl http://api.deploy.localhost/api/apps -H "X-API-Key: $VECTOR_API_KEY"
# → [] (empty list, 200)
```

The dashboard is available at `http://deploy.localhost`.

---

## Deploying your first app

Vector is driven by the `POST /deploy-hook` endpoint. Your CI (GitHub Actions, GitLab, etc.) needs to:

1. Build and push your app's Docker image to DockerHub
2. POST a signed JSON payload to `http://api.deploy.{your-domain}/deploy-hook`

**Request body:**

```json
{
  "app_name": "my-app",
  "image": "your-dockerhub-user/my-app:latest",
  "repo_url": "https://github.com/you/my-app",
  "port": 3000,
  "timestamp": 1712872800000
}
```

**Required headers:**

| Header            | Value                                                                      |
| ----------------- | -------------------------------------------------------------------------- |
| `Content-Type`    | `application/json`                                                         |
| `X-Signature-256` | `sha256=<hex HMAC-SHA256 of body, keyed with VECTOR_DEPLOY_HOOK_SECRET>`   |

**Notes:**

- `app_name` must match `^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$` and is used as the Traefik subdomain: `{app_name}.{namespace}.{your-domain}`
- `timestamp` is milliseconds since epoch; requests older than 5 minutes are rejected as replays
- `repo_url` is displayed in the dashboard, not used for pulling
- `port` is the container's listen port; omit to default to `app.default-port` (3000, override via `APP_DEFAULTPORT`)

**Example GitHub Actions step** (after your image is pushed to DockerHub):

```yaml
- uses: filipnikolov1/vector-deploy-action@v1
  with:
    app: my-app
    image: ${{ env.IMAGE }}
    url: ${{ secrets.VECTOR_DEPLOY_URL }}
    secret: ${{ secrets.VECTOR_HMAC_SECRET }}
```

The composite action signs and POSTs the payload. Action source lives at
`tooling/vector-deploy-action/action.yml`; mirror it to a public
`filipnikolov1/vector-deploy-action` repo tagged `v1` for the `uses:` reference
to resolve in users' workflows.

---

## Environment variables

Humans set **three values** in `.env` (`VECTOR_DOMAIN`, `VECTOR_DASHBOARD_PASSWORD`,
`VECTOR_GEMINI_API_KEY`). `./vector up` auto-generates the internal secrets
(`VECTOR_API_KEY`, `VECTOR_SESSION_SECRET`, `VECTOR_DB_PASS`,
`VECTOR_ANALYZER_DB_PASS`, `VECTOR_DEPLOY_HOOK_SECRET`, `VECTOR_ENCRYPTION_KEY`,
`VECTOR_UPDATER_AUTH_TOKEN`) on first run and never rewrites existing values.
**Back up `VECTOR_ENCRYPTION_KEY`** — losing it makes stored encrypted app env
vars unreadable.

### Hostname derivation

From `VECTOR_DOMAIN` (+ optional `VECTOR_APP_NAMESPACE`, default `apps`):

| Surface | Host | `VECTOR_DOMAIN=localhost` |
|---|---|---|
| Dashboard | `deploy.<domain>` | `deploy.localhost` |
| API / deploy-hook | `api.deploy.<domain>` | `api.deploy.localhost` |
| Deployed apps | `<app>.<namespace>.<domain>` | `myapp.apps.localhost` |

Set `VECTOR_APP_NAMESPACE=` (blank) for `<app>.<domain>`. `VECTOR_DOMAIN_WEB` /
`VECTOR_DOMAIN_API` override individual hosts.

### Optional values

| Var | Enables |
|---|---|
| `VECTOR_GITHUB_TOKEN` | Commit metadata + connect-a-repo (degrades silently) |
| `VECTOR_RESEND_API_KEY`, `VECTOR_RESEND_TO` | Deploy notification emails |
| `VECTOR_DOCKERHUB_USER`, `VECTOR_DOCKERHUB_TOKEN` | Authenticated image pulls |
| `VECTOR_TRUSTED_PROXIES` | Trusted X-Forwarded-For sources for rate limiting |
| `VECTOR_DOCKER_SOCKET_PATH` | Non-default Docker socket host path (macOS) |
| `VECTOR_ACME_EMAIL` | Let's Encrypt notices (HTTPS overlay only) |

### Tuning any Spring property

Tunables no longer have dedicated env vars. Spring's relaxed binding maps any
property to an env var: uppercase, dots → underscores, dashes removed. Examples:

| Property (default) | Env override |
|---|---|
| `vector.ai.provider` (`gemini`) | `VECTOR_AI_PROVIDER=ollama` |
| `vector.ai.gemini.model` (`gemini-2.5-flash`) | `VECTOR_AI_GEMINI_MODEL` |
| `vector.ai.gemini.timeout-seconds` (`30`) | `VECTOR_AI_GEMINI_TIMEOUTSECONDS` |
| `vector.log.retention-days` (`30`) | `VECTOR_LOG_RETENTIONDAYS` |
| `vector.log.retention-deploys` (`5`) | `VECTOR_LOG_RETENTIONDEPLOYS` |
| `analyzer.ai.max-regenerations` (`1`) | `ANALYZER_AI_MAXREGENERATIONS` |
| `app.default-port` (`3000`) | `APP_DEFAULTPORT` |
| `resend.from` | `RESEND_FROM` |
| `vector.ai.base-url` (Ollama) | `VECTOR_AI_BASEURL` |
| `vector.ai.model` (Ollama) | `VECTOR_AI_MODEL` |

Add the override to `.env` (compose forwards it via `env_file`) or the service's
`environment:` block.

---

## Troubleshooting

**`./vector up` fails with a Docker socket error** — check `VECTOR_DOCKER_SOCKET_PATH` matches where your host's socket actually lives. On OrbStack it's `~/.orbstack/run/docker.sock`, not `/var/run/docker.sock`.

**`POST /deploy-hook` returns 401** — your signature is wrong. Verify: 1) you're signing the _raw_ request body (no whitespace changes), 2) the secret on both sides matches, 3) the header is `X-Signature-256: sha256=<hex>` (not `X-Hub-Signature-256`).

**`POST /deploy-hook` returns 401 "Stale deploy request"** — the `timestamp` in the payload is more than 5 minutes off from the server's clock. Check NTP on the VPS.

**`/api/**` returns 403** — missing or wrong `X-API-Key` header. Must match `VECTOR_API_KEY` exactly.

**Ollama requests time out** — first run pulls the model (can take minutes on a small VPS). Either pre-pull it (`docker exec ollama ollama pull llama3.2:3b`) or set `VECTOR_AI_MODEL` to something smaller.
