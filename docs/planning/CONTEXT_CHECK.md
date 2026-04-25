# CONTEXT_CHECK.md
**Audit date:** 2026-04-24 | **Branch:** feat/deploy-progress-toasts

---

## Category 1: Deployment and rollback

---

### Question 1.1 — Rollback feature existence

**Plan assumes:** Rollback is a planned feature to be implemented.

**Reality:** EXISTS

**What was found:**
`POST /api/apps/{appName}/rollback` exists at `DeploymentController.java:100-117`. Accepts `{ "eventId": <Long> }`. Frontend proxy at `frontend/src/app/api/apps/[appName]/rollback/route.ts`. Implementation: validates target event is `DEPLOY_FINISHED` or `MANUAL_ROLLBACK`, re-deploys using stored `imageName`, pins the image post-rollback. Env vars come from **current** state, not historical (`DeploymentServiceImpl.java:186`).

**Gap vs. plan:**
Rollback exists and is functional. No SHA payload — uses `eventId`. Env vars are not historical (plan may assume they should be). Image is auto-pinned after rollback (plan may not account for this side effect).

**Action needed in plans:**
Update all rollback sections to note existing implementation with `eventId` payload, current-env-var behavior, and auto-pin side effect.

---

### Question 1.2 — commit_sha columns

**Plan assumes:** `commit_sha` needs to be added.

**Reality:** EXISTS

**What was found:**
`deployment.commit_sha` at `Deployment.java:40` (`@Column(length=64)`). `deployment_event.commit_sha` at `DeploymentEvent.java:38`. Both populated in tandem: webhook sets deployment SHA (`DeployHookController.java:102`), event records it from request context (`DeploymentEventServiceImpl.java:40-46`).

**Gap vs. plan:**
No gap — both columns exist and are consistently populated.

**Action needed in plans:**
Remove any migration steps that add `commit_sha`; they already exist.

---

### Question 1.3 — last_deployed_at column

**Plan assumes:** Needs to be added (V14 migration).

**Reality:** MISSING

**What was found:**
`Deployment.java` has `createdAt` and `updatedAt` only (lines 32-34). No `lastDeployedAt` or equivalent. `updatedAt` changes on any modification, not just container restarts.

**Gap vs. plan:**
Plan is correct — column is absent. V14 migration is still needed.

**Action needed in plans:**
No change needed; plan assumption is accurate.

---

### Question 1.4 — build_duration_ms tracking

**Plan assumes:** Needs to be added to `deployment` table.

**Reality:** PARTIAL

**What was found:**
`deployment_event.duration_ms` exists (`DeploymentEvent.java:46`) and is populated on BUILD_STARTED/BUILD_FINISHED and DEPLOY_STARTED/DEPLOY_FINISHED events (`DeploymentServiceImpl.java:83,89,143,149`). The `deployment` table has no aggregated `build_duration_ms` column.

**Gap vs. plan:**
Per-event duration exists; deployment-level aggregate does not. Plan to add `build_duration_ms` to `deployment` table is still valid.

**Action needed in plans:**
Note that per-event durations already exist; the migration only needs to add the aggregate column plus a backfill query.

---

### Question 1.5 — TriggerSource enum

**Plan assumes:** May not exist or may be incomplete.

**Reality:** EXISTS

**What was found:**
`TriggerSource.java:3-8` declares `AUTOMATIC, MANUAL, ROLLBACK, RESTART, SELF_UPDATE`. All five values present. `ROLLBACK` is at line 6. Used in `CreateDeploymentRequest` and populated on all event records (`DeploymentEventServiceImpl.java:46`).

**Gap vs. plan:**
No gap. `ROLLBACK` already exists.

**Action needed in plans:**
Remove any step that adds `ROLLBACK` to the enum.

---

### Question 1.6 — rollback_from_sha column

**Plan assumes:** Needs to be added to `deployment_event`.

**Reality:** MISSING

**What was found:**
`DeploymentEvent.java` has no `rollback_from_sha` field. Rollback events have `TriggerSource.ROLLBACK` but no reference to the prior commit SHA. The prior state can only be inferred from event history ordering.

**Gap vs. plan:**
Plan assumption is correct — column does not exist.

**Action needed in plans:**
No change; migration to add `rollback_from_sha` is still needed.

---

### Question 1.7 — Docker image tagging strategy

**Plan assumes:** Images are SHA-tagged by CI.

**Reality:** DIFFERENT

**What was found:**
Launchpad does not construct or verify image tags. `DockerServiceImpl.pullAndRun(imageName, ...)` takes the image name verbatim from the webhook payload (`DeployHookController.java:82`). For rollbacks, the exact `imageName` stored in the target `deployment_event` is reused (`DeploymentServiceImpl.java:188`). Tagging is entirely the CI pipeline's responsibility — Launchpad is tag-agnostic.

**Gap vs. plan:**
Plan may assume SHA-tagged images. Launchpad accepts any tag format; Time Machine assumes old images are still retrievable by tag, which is only true if CI pushes immutable tags (not `latest`-only).

**Action needed in plans:**
Add a prerequisite note: Time Machine rollback requires CI to push immutable tags (e.g., SHA-tagged); `latest`-only pipelines cannot roll back.

---

### Question 1.8 — Image existence checking before deploy

**Plan assumes:** Should be added.

**Reality:** MISSING

**What was found:**
`DockerServiceImpl` has an `imageExistsLocally()` method (`lines 115-120`) but it is never called in the rollback or deploy flow. Regular deploys fail mid-flight if the image is unavailable. No pre-flight validation exists.

**Gap vs. plan:**
Plan assumption is correct — no pre-deploy image check exists.

**Action needed in plans:**
No change; adding pre-deploy image existence check remains a valid task.

---

## Category 2: Events and event bus

---

### Question 2.1 — deployment_event table

**Plan assumes:** May need to be created or expanded.

**Reality:** EXISTS

**What was found:**
`V6__deployment_event.sql` creates the table. Columns: `id, app_name, event_type, status, image_name, branch, commit_sha, commit_message, commit_author, duration_ms, error_message, triggered_by, created_at, finished_at`. Event types (19 total): `DEPLOY_TRIGGERED, BUILD_STARTED, BUILD_FINISHED, DEPLOY_STARTED, DEPLOY_FINISHED, FAILED, CRASHED, RESTARTED, STOPPED, MANUAL_ROLLBACK, WEBHOOK_IGNORED, PIN_RELEASED, UPDATE_AVAILABLE, UPDATE_TRIGGERED, UPDATE_SUCCESS, UPDATE_FAILED, UPDATER_UNREACHABLE, SELF_APP_BOOTSTRAPPED, SUBDOMAIN_CHANGED`.

**Gap vs. plan:**
Table is fully implemented with a comprehensive event type set. Any plan to add new types must check this list first.

**Action needed in plans:**
Audit all planned new event types against this existing enum before adding migrations.

---

### Question 2.2 — CRASHED event emission

**Plan assumes:** CRASHED is never emitted; needs to be wired.

**Reality:** PARTIAL

**What was found:**
`DeploymentEventType.CRASHED` exists (`DeploymentEventType.java:10`). Zero emission sites found. `UptimeMonitorServiceImpl.checkAll()` detects crashes (container not running → status `DOWN`) and calls `notificationService.sendDownAlert()` but never records a `CRASHED` event (`UptimeMonitorServiceImpl.java:41-47`).

**Gap vs. plan:**
Plan assumption is correct — CRASHED exists in enum but is never emitted. Uptime monitor is the natural place to add it.

**Action needed in plans:**
No change; REFACTOR_PLAN Phase 3 fix is accurate.

---

### Question 2.3 — Postgres NOTIFY/LISTEN or outbox

**Plan assumes:** Does not exist; needs to be added in refactor Phase 5.

**Reality:** MISSING

**What was found:**
All event recording is synchronous in-process JPA saves (`DeploymentEventServiceImpl.java:24-48`). No `pg_notify` trigger, no outbox table, no async messaging. Webhook handler is fully synchronous and blocks on Docker operations inside `@Transactional`.

**Gap vs. plan:**
Plan assumption is correct.

**Action needed in plans:**
No change; pg_notify V15 migration and listener are still needed.

---

### Question 2.4 — SSE endpoint

**Plan assumes:** Needs to be added for deployment events.

**Reality:** PARTIAL

**What was found:**
`GET /api/apps/{appName}/logs` returns `TEXT_EVENT_STREAM_VALUE` via `SseEmitter(0L)` (`RuntimeLogController.java:30-57`). Streams **container logs only**. Timeout is 0 (no server-side timeout). No heartbeat mechanism. No `/api/events/stream` endpoint for deployment events. Comments in REFACTOR_PLAN confirm: no heartbeat → disconnect after ~60s idle, no explicit close signal on container stop.

**Gap vs. plan:**
SSE exists for logs; no SSE for deployment events (polling instead). Plan to add deployment-event SSE stream is still needed.

**Action needed in plans:**
Distinguish log SSE (exists) from event SSE (missing); plan steps target the correct one.

---

### Question 2.5 — Frontend event stream consumer

**Plan assumes:** Frontend polls; SSE consumer needs to be added.

**Reality:** PARTIAL

**What was found:**
`useEvents.ts:36` polls with `refreshInterval: 10_000` (SWR). `useBuildLogs.ts:17-38` uses real `EventSource` for container logs. Deployment events have no SSE consumer — polling only. `EventSource` lifecycle in `useBuildLogs.ts`: connects on mount, closes on unmount or error.

**Gap vs. plan:**
Plan assumption correct for deployment events. Log SSE consumer already exists and can be referenced as a pattern.

**Action needed in plans:**
Note `useBuildLogs.ts` as an existing EventSource reference implementation when building the deployment-event SSE hook.

---

## Category 3: Notifications

---

### Question 3.1 — NotificationService state

**Plan assumes:** Needs significant expansion.

**Reality:** PARTIAL

**What was found:**
Interface (`NotificationService.java:1-15`) declares one method: `void sendDownAlert(String appName)`. `NotificationServiceImpl.java:15-66` implements it via Resend REST API (`POST https://api.resend.com/emails`). Uses env vars `resend.api-key`, `resend.from`, `resend.to`. Fails silently if unconfigured (`lines 43-47`).

**Gap vs. plan:**
Only down-alert sending is implemented. No deploy-failed, no recovery alert, no per-event routing.

**Action needed in plans:**
Plan expansion of `NotificationService` interface is correctly scoped.

---

### Question 3.2 — Which events actually send email

**Plan assumes:** Multiple events trigger notifications.

**Reality:** DIFFERENT

**What was found:**
Only one path sends email: `UptimeMonitorServiceImpl.java:46` → `sendDownAlert()` when container transitions to `DOWN`. No email on FAILED events, no recovery emails, no deploy-complete notifications.

**Gap vs. plan:**
Plan assumes broader coverage; reality is minimal.

**Action needed in plans:**
Plan is accurate about the gap; no change needed.

---

### Question 3.3 — Rate limiting on notifications

**Plan assumes:** Needs to be added (15-minute cooldown).

**Reality:** MISSING

**What was found:**
No deduplication, no cooldown, no rate limiting anywhere in `NotificationServiceImpl` or callers. Every monitor cycle that detects `DOWN` will send an email.

**Gap vs. plan:**
Plan assumption is correct.

**Action needed in plans:**
No change; 15-min cooldown via `ConcurrentHashMap<String, Long>` is still needed.

---

### Question 3.4 — NotificationPreference / per-user settings

**Plan assumes:** Not yet implemented.

**Reality:** PARTIAL

**What was found:**
`user_account.preferences` is a JSONB column (`UserAccount.java:29`, `V7__user_account.sql:4`). No service reads or writes it. No frontend UI for notification preferences. The entire `user_account` table is schema-only; no code path touches it.

**Gap vs. plan:**
Schema ghost exists. Any plan to implement preferences can use the existing column but must wire all service and UI layers from scratch.

**Action needed in plans:**
Note that `preferences` column exists; no migration needed to add it.

---

## Category 4: Logs

---

### Question 4.1 — How container logs are fetched

**Plan assumes:** May need redesign.

**Reality:** EXISTS

**What was found:**
Frontend (`useBuildLogs.ts:18`) → Next.js proxy (`app/api/apps/[appName]/logs/runtime/route.ts`) → `GET /api/apps/{appName}/logs` → `RuntimeLogController.streamLogs()` → `DockerService.streamContainerLogs()` → Docker API. Protocol: SSE (`SseEmitter`). Tails last 500 lines (`RuntimeLogController.java:26`). Download endpoint also exists (`/logs/runtime/download`, `RuntimeLogController.java:62-76`).

**Gap vs. plan:**
Log streaming is fully implemented and functional.

**Action needed in plans:**
No gap for current log streaming; Time Machine log persistence is additive.

---

### Question 4.2 — Log persistence

**Plan assumes:** Needs to be added.

**Reality:** MISSING

**What was found:**
No log table, no file-based log storage. Docker is the sole source of truth. Logs are lost when a container is removed. Download endpoint fetches live from Docker daemon.

**Gap vs. plan:**
Plan assumption is correct.

**Action needed in plans:**
No change; `analyzer.log_entry` table with hybrid retention is still needed.

---

### Question 4.3 — Log/event retention policy

**Plan assumes:** No retention exists.

**Reality:** PARTIAL

**What was found:**
Deployments have a soft-delete with 5-minute hard-delete grace period (`DeploymentServiceImpl.java:110-125`, `deletedAt` on `Deployment.java:50`). Deployment events accumulate indefinitely — no TTL. Docker log rotation is daemon-managed (not configured in compose).

**Gap vs. plan:**
Soft-delete for deployments exists; no log or event retention. Plan assumption is partially correct.

**Action needed in plans:**
Distinguish deployment soft-delete (exists) from log/event retention (missing) in planning docs.

---

### Question 4.4 — Virtualized log viewer

**Plan assumes:** Needs to be built.

**Reality:** MISSING

**What was found:**
`useBuildLogs.ts:5` caps at `RING_BUFFER_CAP = 5000` lines. Frontend renders all buffered lines without virtualization. Will lag with large outputs.

**Gap vs. plan:**
Plan assumption is correct.

**Action needed in plans:**
No change; virtualized viewer is still needed.

---

## Category 5: AI integration

---

### Question 5.1 — Ollama integration

**Plan assumes:** Ollama is integrated; may need abstraction.

**Reality:** EXISTS

**What was found:**
`OllamaService.java` + `OllamaServiceImpl.java` in `com.filipnikolov.launchpad.ai.service`. `AiController.java` exposes `POST /api/ai/logs/analyze`. Max log context: 8 KB (`OllamaServiceImpl.java:22`). Docker service `ollama/ollama` on port 11434 with persistent volume `ollama_data`.

**Gap vs. plan:**
Implementation exists and matches plan's description of current state.

**Action needed in plans:**
No change; abstraction phase is still needed but baseline is correct.

---

### Question 5.2 — AI provider abstraction

**Plan assumes:** No abstraction; direct call only.

**Reality:** MISSING (no abstraction)

**What was found:**
`AiController` calls `OllamaService` directly — no `AiProvider` interface. Ollama is a concrete dependency with no injection seam for alternative providers. POST_REFACTOR_ROADMAP Track C plans to introduce `AiProvider` interface.

**Gap vs. plan:**
Plan assumption is correct.

**Action needed in plans:**
No change; Track C abstraction is still needed before Gemini migration.

---

### Question 5.3 — AI request/response DTO shapes

**Plan assumes:** Unknown.

**Reality:** EXISTS

**What was found:**
Request: `OllamaRequest(model, prompt, stream)` (`OllamaServiceImpl.java:36`). Response: `OllamaResponse` record with `.response()` field (`OllamaServiceImpl.java:53`). These are internal DTOs, not exposed in the API surface.

**Gap vs. plan:**
When abstracting to `AiProvider`, these DTOs become implementation details; the interface needs its own provider-agnostic request/response types.

**Action needed in plans:**
Define provider-agnostic DTOs in the `AiProvider` abstraction plan.

---

### Question 5.4 — Ollama reliability (retries, timeouts)

**Plan assumes:** No timeouts; needs 10s read timeout.

**Reality:** PARTIAL

**What was found:**
No `SimpleClientHttpRequestFactory` configured; default `RestClient` timeout (OS-level). `ResourceAccessException` is caught on timeout/connection failure (`OllamaServiceImpl.java:59-64`), returning `AiUnavailableException`. No retries. No rate limiting.

**Gap vs. plan:**
Error path exists; configurable timeout does not. Plan to add explicit 10s timeout is still valid.

**Action needed in plans:**
No change; explicit timeout configuration is still needed.

---

### Question 5.5 — Ollama in docker-compose.yml

**Plan assumes:** Present.

**Reality:** EXISTS

**What was found:**
Service `ollama` (`docker-compose.yml:31-40`), image `ollama/ollama`. Port 11434. Volume `ollama_data:/root/.ollama`. Model set via backend env var `OLLAMA_MODEL=llama3.2:3b`. No memory/CPU limits configured.

**Gap vs. plan:**
No gap; Ollama is present as expected.

**Action needed in plans:**
Note absence of resource limits; VPS deployment may need them.

---

## Category 6: GitHub integration

---

### Question 6.1 — GitHub API client

**Plan assumes:** Basic client exists.

**Reality:** EXISTS

**What was found:**
`GitHubServiceImpl.java` calls `GET /repos/{owner}/{repo}/compare/{base}...{head}`. Returns `CommitsAhead` DTO (commit count + list). No other GitHub endpoints consumed.

**Gap vs. plan:**
Client is minimal — commits-compare only. Time Machine will need additional endpoints (commit detail, file diffs).

**Action needed in plans:**
Scope GitHub client expansion explicitly; current client is a stub relative to Time Machine needs.

---

### Question 6.2 — GitHub token env var

**Plan assumes:** Token is configured.

**Reality:** EXISTS

**What was found:**
`application.properties:30`: `github.token=${GITHUB_TOKEN:}`. Empty default — feature silently degrades if unset (`GitHubServiceImpl.java:54-56`). Sent as `Authorization: Bearer {token}`. Scope needed: `public_repo` read minimum.

**Gap vs. plan:**
No gap; token handling matches assumed pattern.

**Action needed in plans:**
No change needed.

---

### Question 6.3 — GitHub response caching

**Plan assumes:** May not exist.

**Reality:** EXISTS

**What was found:**
`ConcurrentHashMap<String, CacheEntry>` in `GitHubServiceImpl.java:24`. TTL: 60 seconds (`CACHE_TTL_MS = 60_000`, line 19). Key: `repoUrl|base|head`. In-memory only; lost on restart.

**Gap vs. plan:**
Cache exists; not mentioned in plans. Time Machine commit-detail fetching will need this cache or a DB-backed equivalent.

**Action needed in plans:**
Note existing in-memory cache; decide if TTL needs adjustment for Time Machine's heavier GitHub usage.

---

### Question 6.4 — GitHub features consumed

**Plan assumes:** Webhook intake + compare.

**Reality:** PARTIAL

**What was found:**
Commits-compare only (`/compare/{base}...{head}`). No webhook registration, no blame, no file content, no PR APIs. Launchpad receives webhooks but doesn't register them via GitHub API.

**Gap vs. plan:**
Plan may assume more GitHub coverage. Time Machine diff/blame features need new client methods.

**Action needed in plans:**
Enumerate required new GitHub API calls for Time Machine explicitly.

---

## Category 7: Authentication and authorization

---

### Question 7.1 — Auth mechanism

**Plan assumes:** Single shared API key.

**Reality:** EXISTS

**What was found:**
`ApiKeyAuthFilter.java:1-62` intercepts all `/api/**` requests. Validates `X-API-Key` header via constant-time comparison. No JWT, no session on backend. Single key shared across all callers.

**Gap vs. plan:**
Matches plan assumptions exactly.

**Action needed in plans:**
No change.

---

### Question 7.2 — Cookie vs. header auth

**Plan assumes:** Both patterns exist.

**Reality:** EXISTS

**What was found:**
Backend: header-only (`X-API-Key`). Next.js frontend: cookie-based session (`SESSION_SECRET` in `docker-compose.yml:82`). Next.js proxy adds `X-API-Key` server-side before forwarding — browser never sends the API key directly.

**Gap vs. plan:**
Two-layer auth model is working as intended.

**Action needed in plans:**
No change.

---

### Question 7.3 — user_account table

**Plan assumes:** May need to be removed or is unused.

**Reality:** PARTIAL

**What was found:**
Schema exists (`V7__user_account.sql`): `id, email, api_key_hash, preferences (JSONB), created_at`. No Java code reads or writes this table. `UserAccount.java` entity exists but is orphaned. POST_REFACTOR_ROADMAP Decision D5 flags `api_key_hash` for removal in single-user model.

**Gap vs. plan:**
Table is a schema ghost. Plan to drop `api_key_hash` is valid.

**Action needed in plans:**
No change; D5 removal plan is accurate.

---

### Question 7.4 — Next.js API key passing

**Plan assumes:** Server-side proxy injects the key.

**Reality:** EXISTS

**What was found:**
All backend calls go through Next.js server-side route handlers. Each reads `APP_API_KEY` from server env and adds `X-API-Key` header. Browser never receives or sends the API key. Pattern is consistent across all proxy routes.

**Gap vs. plan:**
No gap; implementation matches assumed pattern.

**Action needed in plans:**
No change.

---

### Question 7.5 — All Next.js API proxy routes

**Plan assumes:** Standard set.

**Reality:** EXISTS (27 routes found)

**What was found:**
Full list:
- `api/ai/logs/analyze` → `POST /api/ai/logs/analyze`
- `api/apps/[appName]/commits-ahead` → `GET /api/apps/{appName}/commits-ahead`
- `api/apps/[appName]/env/[key]` → env var CRUD
- `api/apps/[appName]/env` → env var list/set
- `api/apps/[appName]/events` → `GET /api/apps/{appName}/events`
- `api/apps/[appName]/logs/runtime/download` → log download
- `api/apps/[appName]/logs/runtime` → SSE log stream
- `api/apps/[appName]/restart` → `POST /api/apps/{appName}/restart`
- `api/apps/[appName]/restore` → `POST /api/apps/{appName}/restore`
- `api/apps/[appName]/rollback` → `POST /api/apps/{appName}/rollback`
- `api/apps/[appName]` → `GET /api/apps/{appName}`
- `api/apps/[appName]/stats` → container stats
- `api/apps/[appName]/stop` → `POST /api/apps/{appName}/stop`
- `api/apps/[appName]/subdomain` → `PATCH /api/apps/{appName}/subdomain`
- `api/apps/[appName]/unpin` → `POST /api/apps/{appName}/unpin`
- `api/apps` → `GET /api/apps`
- `api/auth/login` → frontend session only (no backend)
- `api/auth/logout` → frontend session clear only
- `api/deploy-hook/curl-template` → `GET /deploy-hook/template`
- `api/events` → `GET /api/events` (global, 10s poll)
- `api/me/pin/[appName]` → user preferences (frontend-only)
- `api/me/preferences` → user preferences (frontend-only)
- `api/me` → user info (frontend-only)
- `api/self-apps/[appName]/pending` → `GET /api/self-apps/{appName}/pending`
- `api/self-apps/[appName]/update` → `POST /api/self-apps/{appName}/update`
- `api/setup/deploy-url` → `GET /api/setup/deploy-url`
- `api/setup/status` → `GET /api/setup/status`

**Gap vs. plan:**
`/restore`, `/unpin`, `/self-apps/*` routes may not be in planning docs. `api/me/*` routes are frontend-only with no backend equivalent.

**Action needed in plans:**
Cross-reference Time Machine's new endpoints against this list to avoid duplicates.

---

## Category 8: Docker and infrastructure

---

### Question 8.1 — Services in docker-compose.yml

**Plan assumes:** Standard set.

**Reality:** EXISTS

**What was found:**
| Service | Role |
|---------|------|
| `traefik` v3.6 | Reverse proxy + routing |
| `postgres` 16 | Primary database |
| `ollama` | AI log analysis |
| `launchpad` | Spring Boot backend API |
| `launchpad-frontend` | Next.js web dashboard |
| `launchpad-updater` | Self-update mechanism |

**Gap vs. plan:**
No gap; all services match expected composition.

**Action needed in plans:**
No change.

---

### Question 8.2 — Docker socket mounts

**Plan assumes:** Backend has socket access.

**Reality:** EXISTS

**What was found:**
`traefik`: `docker.sock:ro`. `launchpad` backend: `docker.sock` (read-write). `launchpad-updater`: `docker.sock` (read-write). Frontend has no socket access.

**Gap vs. plan:**
No gap.

**Action needed in plans:**
No change.

---

### Question 8.3 — Traefik SSE configuration

**Plan assumes:** `flushinterval` needs to be added.

**Reality:** MISSING

**What was found:**
No `responseforwarding.flushinterval` label on any service. Backend label only sets `loadbalancer.server.port=8082`. Without flush interval, Traefik buffers SSE chunks — confirmed gap cited in REFACTOR_PLAN Finding #7.

**Gap vs. plan:**
Plan assumption is correct.

**Action needed in plans:**
No change; `flushinterval=100ms` labels must be added.

---

### Question 8.4 — Backend container healthcheck

**Plan assumes:** Not audited.

**Reality:** MISSING

**What was found:**
`postgres` service has `pg_isready` healthcheck (`docker-compose.yml:25-29`). `launchpad` backend has none. Spring Actuator is available but not configured as a Docker healthcheck.

**Gap vs. plan:**
No healthcheck exists; Actuator endpoint could be used but is not wired.

**Action needed in plans:**
Add Docker healthcheck wiring to backend if considered in VPS hardening phase.

---

### Question 8.5 — Backend env vars

**Plan assumes:** Standard set.

**Reality:** EXISTS

**What was found:**
| Var | Required | Notes |
|-----|----------|-------|
| `DB_URL` | Yes | Default: `jdbc:postgresql://postgres:5432/launchpad` |
| `DB_USERNAME` | Yes | |
| `DB_PASSWORD` | Yes | |
| `DOCKER_SOCKET` | Yes | Default: `unix:///var/run/docker.sock` |
| `DOCKERHUB_USERNAME` | No | |
| `DOCKERHUB_TOKEN` | No | |
| `APP_DEFAULT_PORT` | No | Default: 3000 |
| `ENCRYPTION_KEY` | Yes | AES key for env var storage |
| `RESEND_API_KEY` | No | Notifications disabled if missing |
| `RESEND_FROM` | No | |
| `RESEND_TO` | No | |
| `APP_API_KEY` | Yes | 503 if missing |
| `GITHUB_TOKEN` | No | Feature degrades silently |
| `OLLAMA_BASE_URL` | No | Default: `http://localhost:11434` |
| `OLLAMA_MODEL` | No | Default: `llama3.2:3b` |
| `DEPLOY_HOOK_SECRET` | Yes | Webhook HMAC validation |
| `TRAEFIK_NETWORK` | No | |

**Gap vs. plan:**
`ENCRYPTION_KEY` may be underdocumented in planning docs; it is required for the env var storage feature.

**Action needed in plans:**
Ensure `ENCRYPTION_KEY` is in all setup/VPS deployment checklists.

---

### Question 8.6 — Actuator endpoint

**Plan assumes:** Unknown.

**Reality:** PARTIAL

**What was found:**
`spring-boot-starter-actuator` dependency present in `pom.xml`. `/actuator/health` available on port 8082. Not referenced in `docker-compose.yml` healthcheck. No custom actuator configuration found in `application.properties`.

**Gap vs. plan:**
Actuator is available but unused as a healthcheck signal.

**Action needed in plans:**
No change needed unless healthcheck wiring is in scope.

---

## Category 9: Frontend architecture

---

### Question 9.1 — State management

**Plan assumes:** React hooks + SWR.

**Reality:** EXISTS

**What was found:**
SWR for server state, `useState`/`useReducer` for local state. No Zustand, Jotai, or Redux in `package.json`. Single context: `ToastProvider`. Pattern is consistent across all hooks (`useEvents.ts`, `useBuildLogs.ts`, `useContainerStats.ts`).

**Gap vs. plan:**
No gap; plan assumptions are correct.

**Action needed in plans:**
No change.

---

### Question 9.2 — ErrorBoundary component

**Plan assumes:** Needs to be added.

**Reality:** MISSING

**What was found:**
No `ErrorBoundary` component found in `components/`. No `error.tsx` Next.js error page found. Unhandled render errors will surface as blank screen.

**Gap vs. plan:**
Plan assumption is correct.

**Action needed in plans:**
No change; ErrorBoundary addition is still needed.

---

### Question 9.3 — Toast/notification system

**Plan assumes:** Needs to be added or expanded.

**Reality:** EXISTS

**What was found:**
`hooks/useToast.tsx` + `components/primitives/Toast.tsx`. API: `toast.success()`, `toast.error()`, `toast.info()`, `toast.progress()`. Cap: 3 toasts (`useToast.tsx:53` `.slice(-3)`). Supports `persistent: true` flag to bypass auto-dismiss. Context-based, globally accessible.

**Gap vs. plan:**
Toast system exists and is feature-complete. REFACTOR_PLAN flags the 3-toast cap as potentially insufficient for concurrent progress toasts.

**Action needed in plans:**
Reference existing toast API in any plan that adds new toast calls; avoid re-implementing.

---

### Question 9.4 — Routing pattern

**Plan assumes:** App Router.

**Reality:** EXISTS

**What was found:**
All routes use Next.js App Router (`app/` directory). No `pages/` directory remnants found. Routes confirmed: `/apps`, `/setup`, `/settings`.

**Gap vs. plan:**
No gap.

**Action needed in plans:**
No change.

---

### Question 9.5 — Component library

**Plan assumes:** Basic primitives exist.

**Reality:** PARTIAL

**What was found:**
`components/primitives/` directory exists with ~8 components (Button, Card, GlassCard, Toast, Input inferred from usage patterns). No external component library (no shadcn, no MUI). Custom CSS variables for theme (`text-status-running`, `accent-ghostLight` etc.).

**Gap vs. plan:**
Primitive library is minimal; Time Machine UI will require new components.

**Action needed in plans:**
Identify which Time Machine UI components need to be built from scratch vs. composable from primitives.

---

### Question 9.6 — Existing timeline/commit history display

**Plan assumes:** Time Machine is greenfield.

**Reality:** MISSING

**What was found:**
No timeline component, no commit history view, no deployment comparison UI. Rollback is API-only (proxy route exists, no UI). `useEvents.ts` renders a flat activity feed with no timeline visualization.

**Gap vs. plan:**
Plan assumption is correct; Time Machine UI is fully greenfield.

**Action needed in plans:**
No change.

---

## Category 10: Pending work and known issues

---

### Question 10.1 — Backend TODO/FIXME comments

**Plan assumes:** Some exist.

**Reality:** MISSING

**What was found:**
Grep for `TODO|FIXME|HACK|XXX` in `backend/src/**/*.java` returned zero matches.

**Gap vs. plan:**
No code comments flag pending work; issues are tracked in planning docs only.

**Action needed in plans:**
No change.

---

### Question 10.2 — Frontend TODO/FIXME comments

**Plan assumes:** Some exist.

**Reality:** MISSING

**What was found:**
Grep in `frontend/src/**/*.ts,tsx` (excluding `node_modules`) returned zero matches.

**Gap vs. plan:**
No code comments flag pending work.

**Action needed in plans:**
No change.

---

### Question 10.3 — Backup/deprecated files

**Plan assumes:** Unknown.

**Reality:** MISSING

**What was found:**
No files matching `*_backup.*`, `*_old.*`, `*.bak`, or `*.deprecated` found in repo.

**Gap vs. plan:**
No abandoned file artifacts present.

**Action needed in plans:**
No change.

---

### Question 10.4 — Commented-out large code blocks

**Plan assumes:** Unknown.

**Reality:** UNABLE TO DETERMINE

Spot-checks of key files (DeploymentServiceImpl, RuntimeLogController, OllamaServiceImpl) found no large commented blocks. Exhaustive scan of all files not performed.

**Action needed in plans:**
No change; low risk given clean TODO/FIXME state.

---

### Question 10.5 — CHANGELOG/ROADMAP files

**Plan assumes:** May exist.

**Reality:** PARTIAL

**What was found:**
No `CHANGELOG.md`. Planning docs present: `REFACTOR_PLAN.md`, `TIME_MACHINE_PLAN.md`, `POST_REFACTOR_ROADMAP.md`, `THEME_UPGRADE.md` (all untracked per git status). No `ROADMAP.md` with that exact name.

**Gap vs. plan:**
Planning docs exist and are comprehensive but are untracked files (`.gitignore` or intentionally local only).

**Action needed in plans:**
No change.

---

## Category 11: Tests

---

### Question 11.1 — Test count and coverage pattern

**Plan assumes:** Unknown.

**Reality:** PARTIAL

**What was found:**
~22 test files found in `backend/src/test/`. Pattern inferred as JUnit5 + MockMvc (Spring Boot standard). No E2E or integration test suite found. Frontend has no test files. Specific coverage percentages not determined.

**Gap vs. plan:**
No frontend tests. Backend test count is modest for the codebase size.

**Action needed in plans:**
No change unless test coverage is a refactor exit criterion.

---

## Category 12: Anything else notable

---

### Question 12.1 — Undocumented subsystems

**What was found:**

1. **Soft-delete with undo window** (`Deployment.java:50` `deletedAt`, `DeploymentServiceImpl.java:110-125`): 5-minute grace period before hard delete. `POST /api/apps/{appName}/restore` exists. Not mentioned in planning docs but affects Time Machine design (deleted deployments appear in history).

2. **Pin/unpin image** (`Deployment.java:52-55` `pinnedImage`, `pinnedAt`): Locks a deployment to a specific image. Webhook returns `UPDATE_AVAILABLE` instead of deploying when pinned (`DeployHookController.java:120-138`). Rollback auto-pins the target image. Time Machine rollback flow must account for this.

3. **ActionLockService** (prevents concurrent ops on same app, 5-second lock, 409 on contention): All mutating endpoints (restart, stop, rollback, subdomain, deploy) acquire this lock. Time Machine rollback will hit this path.

4. **Env var encryption at rest** (`EncryptionService` + `EnvVarService`): AES encryption, `ENCRYPTION_KEY` required. Env vars are not plain-text in DB. Time Machine's env-var snapshot feature must handle encrypted values.

5. **`user_account` table schema ghost**: Table and entity exist (`V7__user_account.sql`, `UserAccount.java`) but zero code reads or writes it. `api_key_hash` column present but unused. Safe to drop per POST_REFACTOR_ROADMAP D5, but migration must exist.

---

*End of CONTEXT_CHECK.md*
