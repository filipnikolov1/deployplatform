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

The supported path is the dashboard's **Connect a repository** flow (below) — install the GitHub
App, pick a repo, confirm modules, and Vector wires CI and deploys automatically. No secrets to
generate or sign yourself.

---

## Connect a repository

The dashboard's "Connect a repository" flow installs a GitHub App once and takes care of CI
wiring, module detection, and deploys for you.

**Install flow:**

1. From the dashboard, follow the manifest-based GitHub App install link. GitHub creates the App
   and hands Vector its credentials automatically — no manual PEM/ID entry.
2. Pick which repos to grant the App access to (you can add more later from GitHub's own
   installation settings page).
3. Pick a repo and Connect. Vector scans it (file tree + manifests, AI-assisted for ambiguous
   monorepos) and proposes one or more modules — confirm names, ports, env vars, and which modules
   are exposed to the web.

**Friend-approval flow:** anyone can install the App on their own repos and point it at your
instance. Installations made by an account other than your own land as **pending approval** —
their repos are visible but not connectable until you approve them from the dashboard. Rejected
installations stay listed (re-approvable) but stay silent.

**Managed vs custom workflow lanes:** connecting a repo picks one of two lanes per app:

- **Managed** — Vector writes and owns `.github/workflows/vector-deploy.yml` (marked with a
  `# vector:managed` header; Vector may upgrade the template later, always audited). One build job
  per module, buildpacks by default (Dockerfile fallback), pushes to `ghcr.io` using the
  workflow's own `GITHUB_TOKEN` — no registry credentials are ever written to your repo.
- **Custom** — used automatically when the repo already has its own workflow, or you opt out.
  Vector never writes or overwrites a workflow it didn't create. Add this to your existing
  workflow to stay event-driven:

  ```yaml
  permissions:
    contents: read
    packages: write
  steps:
    - name: Log in to GHCR
      run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
    - name: Build and push
      run: |
        docker build -t image:${{ github.sha }} .
        docker push image:${{ github.sha }}
  ```

  Name the job `build-<app-name>` so Vector's webhook listener can match it to the right app.

Either lane is fully event-driven: your CI just builds and pushes; Vector's GitHub App webhook
(`workflow_run`/`workflow_job` events) picks up the push, streams live build progress, and swaps
the running container once the image is ready. There's no deploy-hook call to make or secret to
manage.

**DB provisioning:** if the scan detects likely Postgres usage (a `prisma/` dir, `DATABASE_URL` in
`.env.example`, a `postgres:` compose service, etc.) the confirm step offers one-click
provisioning — a database + user in a shared `apps-postgres` container, with `DATABASE_URL`
injected into the app's env vars automatically. **Orphan policy:** deleting an app never drops its
database; it's flagged orphaned in the Databases panel with size/owner shown, and dropping it is
always an explicit, separate action.

**Quick Deploy:** already have a pre-built image? `POST /api/deploy/quick` (dashboard session
auth, no HMAC) takes `{ image, appName, port, env{}, subdomain? }` and deploys it through the same
pipeline as a connected repo, tagged `deploy_source=QUICK`. Docker runtime only for now.

**Inter-service configuration:** apps that come from a multi-module repo are grouped into a
project. Each service automatically receives `SERVICE_<NAME>_URL` env vars pointing at its
siblings' public URLs (e.g. a `shop-api` service gets `SERVICE_SHOP_WEB_URL`), sanitized
upper-case with non-alphanumerics replaced by `_`, recomputed on every deploy. A project also has
its own shared env vars, editable from the dashboard. Precedence, lowest to highest: injected
`SERVICE_*`/`PORT` vars → project shared vars → the app's own env vars (manual values always win).
For CI **build-time** vars (e.g. Next.js `NEXT_PUBLIC_API_URL` baked in at build, not runtime) —
add the value as a repo Actions **variable** and reference it in your build step manually; Vector
does not inject build-time vars into managed workflows.

**Notes:**

- `VECTOR_GITHUB_TOKEN` remains only for legacy single-file reads (commit metadata) and is unused
  once the GitHub App is configured.
- **Uninstalling the GitHub App on GitHub does not stop deploys** — the workflow and any state it
  needs stay in the repo. Deleting the connected app from the dashboard is what stops them;
  subsequent webhook deliveries for a removed app are rejected as unknown (visible in the webhook
  inspector once that UX ships).
- **Quick Deploy of private images:** private `ghcr.io` images from repos you've connected
  authenticate automatically using the GitHub App's installation token — nothing to configure.
  Private DockerHub images still need `VECTOR_DOCKERHUB_USER` / `VECTOR_DOCKERHUB_TOKEN` set (see
  Environment variables below).
- **Existing installations must re-approve permissions:** the GitHub App manifest now requests
  `packages: read` (for private GHCR pulls). If you installed the App before this change, GitHub
  will prompt you to accept the new permission on the App's installation settings page — deploys
  of private GHCR images silently pull without auth until you do.

---

## Environment variables

Humans set **three values** in `.env` (`VECTOR_DOMAIN`, `VECTOR_DASHBOARD_PASSWORD`,
`VECTOR_GEMINI_API_KEY`). `./vector up` auto-generates the internal secrets
(`VECTOR_API_KEY`, `VECTOR_SESSION_SECRET`, `VECTOR_DB_PASS`,
`VECTOR_ANALYZER_DB_PASS`, `VECTOR_DEPLOY_HOOK_SECRET`, `VECTOR_ENCRYPTION_KEY`,
`VECTOR_UPDATER_AUTH_TOKEN`) on first run and never rewrites existing values.
**Back up `VECTOR_ENCRYPTION_KEY`** — losing it makes stored encrypted app env
vars unreadable.

### Upgrading an existing deployment

`./vector` only generates a secret when its var is absent — it never rewrites
an existing `.env`. But the Postgres init scripts that create the `launchpad`
and `vector_analyzer` DB roles only run once, on a fresh volume. If you have
an existing volume that relied on the old compose defaults (`VECTOR_DB_PASS`
defaulted to `launchpad`, `VECTOR_ANALYZER_DB_PASS` to `vector_analyzer`),
either set those two vars in `.env` to the old values before running
`./vector up` so the wrapper keeps them, or after upgrading run inside the
postgres container:

```
ALTER USER launchpad WITH PASSWORD '<generated VECTOR_DB_PASS>';
ALTER USER vector_analyzer WITH PASSWORD '<generated VECTOR_ANALYZER_DB_PASS>';
```

to match the values `./vector` generated into `.env`.

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
| `VECTOR_GITHUB_TOKEN` | Legacy single-file reads only; unused once the GitHub App is configured |
| `VECTOR_GITHUB_APP_ID`, `VECTOR_GITHUB_APP_PRIVATE_KEY`, `VECTOR_GITHUB_APP_WEBHOOK_SECRET` | Override the wizard-stored GitHub App credentials (GitOps/k8s path) |
| `VECTOR_RESEND_API_KEY`, `VECTOR_RESEND_TO` | Deploy notification emails |
| `VECTOR_DOCKERHUB_USER`, `VECTOR_DOCKERHUB_TOKEN` | Authenticated pulls of private DockerHub images |
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
