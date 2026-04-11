# Launchpad

A personal PaaS for deploying Dockerized apps on a single VPS. Launchpad receives an HMAC-signed deploy request from your CI, pulls the new image from DockerHub, recreates the container behind Traefik on its own subdomain, and surfaces live build logs + deploy status in a small ops dashboard.

It's a portfolio project built as a real tool — you can plug it into your own VPS today and use it to host other side projects.

---

## Architecture at a glance

```
GitHub Actions ──push──▶  DockerHub
      │
      └──HMAC POST──▶  Launchpad backend  ──pull──▶  DockerHub
                              │
                              ├──▶  Docker daemon  (create/start/stop containers)
                              ├──▶  Postgres       (deployments, env vars, users)
                              ├──▶  Ollama         (AI log analysis)
                              └──▶  Resend         (deploy success/failure email)

User's browser ──▶  Traefik  ──▶  Launchpad frontend (dashboard)
                         └─▶  Deployed apps at `{app}.{your-domain}`
```

Everything runs as Docker containers managed by a single `docker-compose.yml`. Traefik is the only exposed port on the host (80/443); everything else lives on the internal `traefik` network.

### Services in docker-compose

| Service      | What it does                                                        |
| ------------ | ------------------------------------------------------------------- |
| `traefik`    | Reverse proxy; routes host → container by Docker labels             |
| `postgres`   | Stores deployments, encrypted env vars, users                       |
| `ollama`     | Local LLM for "Ask AI: Analyze Logs" in the dashboard               |
| `launchpad`  | The backend — Spring Boot, talks to Docker socket, runs deploys     |
| `watchtower` | Auto-pulls the latest `launchpad` image so Launchpad updates itself |

The frontend dashboard is a separate Next.js container (planned — see `docs/superpowers/specs/`).

---

## Quick start (local dev on macOS/Linux)

**Prerequisites:**

- Docker (Desktop / OrbStack / Colima)
- JDK 21 if you want to run the backend outside Docker
- Node 20 if you want to run the frontend outside Docker

**1. Clone and copy env:**

```bash
git clone <this-repo> launchpad
cd launchpad
cp .env.example .env
```

**2. Fill in `.env`** — see the full reference below. At minimum you need:

- `DEPLOY_HOOK_SECRET` — any random string; your CI will sign deploys with this
- `ENCRYPTION_KEY` — `openssl rand -base64 32`
- `APP_API_KEY` — `openssl rand -hex 32`
- `DB_PASSWORD` — any non-default value
- `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` — read-only token from https://hub.docker.com/settings/security
- `DOCKER_SOCKET_PATH` — path to your host's Docker socket (defaults to `/var/run/docker.sock`; OrbStack users: `~/.orbstack/run/docker.sock`)

**3. Bring it up:**

```bash
docker compose up -d
```

**4. Sanity check:**

```bash
curl http://launchpad.localhost/api/apps -H "X-API-Key: $APP_API_KEY"
# → [] (empty list, 200)
```

If you're on macOS and `launchpad.localhost` doesn't resolve, add it to `/etc/hosts`.

---

## Deploying your first app

Launchpad is driven by the `POST /deploy-hook` endpoint. Your CI (GitHub Actions, GitLab, etc.) needs to:

1. Build and push your app's Docker image to DockerHub
2. POST a signed JSON payload to `http://launchpad.{your-domain}/deploy-hook`

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

| Header            | Value                                                             |
| ----------------- | ----------------------------------------------------------------- |
| `Content-Type`    | `application/json`                                                |
| `X-Signature-256` | `sha256=<hex HMAC-SHA256 of body, keyed with DEPLOY_HOOK_SECRET>` |

**Notes:**

- `app_name` must match `^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$` and is used as the Traefik subdomain: `{app_name}.{your-domain}`
- `timestamp` is milliseconds since epoch; requests older than 5 minutes are rejected as replays
- `repo_url` is displayed in the dashboard, not used for pulling
- `port` is the container's listen port; omit to default to `APP_DEFAULT_PORT` (3000)

**Example GitHub Actions step** (after your image is pushed to DockerHub):

```yaml
- name: Trigger deploy
  env:
     DEPLOY_HOOK_SECRET: ${{ secrets.DEPLOY_HOOK_SECRET }}
  run: |
     PAYLOAD=$(jq -nc \
       --arg app "my-app" \
       --arg image "$DOCKERHUB_USER/my-app:latest" \
       --arg repo "https://github.com/${{ github.repository }}" \
       --argjson port 3000 \
       --argjson ts $(($(date +%s) * 1000)) \
       '{app_name:$app, image:$image, repo_url:$repo, port:$port, timestamp:$ts}')
     SIG=$(printf '%s' "$PAYLOAD" | openssl dgst -sha256 -hmac "$DEPLOY_HOOK_SECRET" -hex | awk '{print $2}')
     curl -fsS -X POST https://launchpad.your-domain/deploy-hook \
       -H "Content-Type: application/json" \
       -H "X-Signature-256: sha256=$SIG" \
       --data "$PAYLOAD"
```

---

## Environment variables

Copy `.env.example` to `.env` and fill in. Everything below reflects `.env.example`.

### Database

| Var           | Default                                     | Purpose                                                                                                                                                        |
| ------------- | ------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DB_URL`      | `jdbc:postgresql://postgres:5432/launchpad` | JDBC URL. Use the compose default when running in Docker; override to `localhost:5433` (or wherever) if you run the backend directly against a local Postgres. |
| `DB_USERNAME` | `launchpad`                                 | Postgres user. Also used as the database name by compose.                                                                                                      |
| `DB_PASSWORD` | _(required)_                                | Postgres password. Change from the example.                                                                                                                    |

### Deploy hook

| Var                  | Default      | Purpose                                                                   |
| -------------------- | ------------ | ------------------------------------------------------------------------- |
| `DEPLOY_HOOK_SECRET` | _(required)_ | Shared HMAC secret between Launchpad and your CI. `openssl rand -hex 32`. |

### Docker

| Var                  | Default                       | Purpose                                                                                                                                                                         |
| -------------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DOCKER_SOCKET`      | `unix:///var/run/docker.sock` | Where the backend process reaches the Docker daemon (from _inside_ its container). Leave as default.                                                                            |
| `DOCKER_SOCKET_PATH` | `/var/run/docker.sock`        | Where the Docker socket lives on the _host_. Compose mounts this into the container. OrbStack on macOS: `~/.orbstack/run/docker.sock`. Colima: `~/.colima/default/docker.sock`. |

### DockerHub

| Var                  | Default   | Purpose                                                                                       |
| -------------------- | --------- | --------------------------------------------------------------------------------------------- |
| `DOCKERHUB_USERNAME` | _(empty)_ | Your DockerHub username. Required to pull private images; optional for public.                |
| `DOCKERHUB_TOKEN`    | _(empty)_ | DockerHub access token. Create a read-only token at https://hub.docker.com/settings/security. |

### Deploy defaults

| Var                | Default | Purpose                                                     |
| ------------------ | ------- | ----------------------------------------------------------- |
| `APP_DEFAULT_PORT` | `3000`  | Fallback container port if the deploy payload omits `port`. |

### Encryption

| Var              | Default      | Purpose                                                                                                                                                                                                     |
| ---------------- | ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ENCRYPTION_KEY` | _(required)_ | AES key used to encrypt stored env vars before writing to Postgres. `openssl rand -base64 32`. **Rotating this invalidates all previously stored env vars** — don't change it after deploys start using it. |

### Notifications (Resend)

| Var              | Default                             | Purpose                                                                               |
| ---------------- | ----------------------------------- | ------------------------------------------------------------------------------------- |
| `RESEND_API_KEY` | _(empty)_                           | API key from https://resend.com. Leave blank to disable email notifications entirely. |
| `RESEND_FROM`    | `Launchpad <onboarding@resend.dev>` | From address. Works as-is for testing; use your own verified domain for real use.     |
| `RESEND_TO`      | _(empty)_                           | Where deploy success/failure emails go. Your email.                                   |

### Launchpad API key

| Var           | Default      | Purpose                                                                                                                                                                                       |
| ------------- | ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `APP_API_KEY` | _(required)_ | Shared secret required on the `X-API-Key` header for every `/api/**` request (everything but the public `/deploy-hook` endpoint and the public build-log SSE stream). `openssl rand -hex 32`. |

### Ollama (AI log analysis)

| Var               | Default                  | Purpose                                                                                                |
| ----------------- | ------------------------ | ------------------------------------------------------------------------------------------------------ |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL. When running via compose this is overridden automatically to `http://ollama:11434`. |
| `OLLAMA_MODEL`    | `llama3.2:3b`            | Model to use for log analysis. Anything Ollama can pull; smaller is faster.                            |

---

## Project layout

```
launchpad/
├── backend/            # Spring Boot app — Deployment service, Docker integration,
│                       #    deploy-hook receiver, encrypted env vars, Ollama client
├── frontend/           # Next.js dashboard (planned, see docs/superpowers/specs/)
├── docker-compose.yml  # Full local + prod stack
├── .env.example        # Every env var documented
└── docs/
    └── launchpad-wiki/ # Architectural notes, Obsidian-friendly
```

---

## Troubleshooting

**`docker compose up` fails with a Docker socket error** — check `DOCKER_SOCKET_PATH` matches where your host's socket actually lives. On OrbStack it's `~/.orbstack/run/docker.sock`, not `/var/run/docker.sock`.

**`POST /deploy-hook` returns 401** — your signature is wrong. Verify: 1) you're signing the _raw_ request body (no whitespace changes), 2) the secret on both sides matches, 3) the header is `X-Signature-256: sha256=<hex>` (not `X-Hub-Signature-256`).

**`POST /deploy-hook` returns 401 "Stale deploy request"** — the `timestamp` in the payload is more than 5 minutes off from the server's clock. Check NTP on the VPS.

**`/api/**`returns 403** — missing or wrong`X-API-Key`header. Must match`APP_API_KEY` exactly.

**Ollama requests time out** — first run pulls the model (can take minutes on a small VPS). Either pre-pull it (`docker exec ollama ollama pull llama3.2:3b`) or set `OLLAMA_MODEL` to something smaller.
