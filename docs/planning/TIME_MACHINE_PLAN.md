# Time Machine — implementation plan

**Status:** planned, not started
**Prerequisites:** refactor phases 1–5 complete, Vector rename + monorepo restructure complete (see `POST_REFACTOR_ROADMAP.md`)
**Estimated effort:** 3–4 weeks of focused evenings
**Service name:** `vector-analyzer` (container/image/folder) · "Time Machine" (UI)

> **Amendment applied 2026-04-24** — this plan was updated per `CONTEXT_CHECK.md` findings. Key changes: rollback endpoint already exists and is reused (no new backend work); `TriggerSource.ROLLBACK` already exists; `/api/apps/[appName]/rollback` and `/api/apps/[appName]/unpin` proxies already exist; new §4.10 covers pin/unpin, ActionLockService, and soft-delete behaviors that weren't in the original plans. See `PLAN_AMENDMENTS.md` for the full audit trail.

This document is self-contained. When executing, you shouldn't need to re-read the design conversations — everything is here.

---

## 1. What Time Machine is

A per-app historical view integrated into the Vector dashboard. Accessible for every deployed app at all times. The view adapts to the app's state:

- **Normal state:** timeline of deploys/commits/events + recent deploys list + click-any-commit to see logs, diff, and file state at that point + one-click rollback
- **Crash state:** same view upgraded with playback-style crash reconstruction, scrubbed log + code panels with a red flash on the error, AI analysis with inline citations, and a receipts panel showing exact evidence

One entry point, one mental model, two levels of depth depending on whether something's broken.

Backed by a new microservice, `vector-analyzer`, which runs independently of `vector-api`. If `vector-api` is down, Time Machine still renders historical data from its own schema. If `vector-analyzer` is down, the main dashboard works fine — Time Machine shows a degraded "analysis unavailable" banner.

---

## 2. Scope locked (do not re-derive during execution)

### In scope for MVP
- Time Machine tab in sidebar and per-app entry point
- Timeline visualization with deploy/commit/suspect/restart/crash event types
- Recent Deploys list (interleaved commits + deployments)
- Commit detail view with three tabs: Logs / Diff / File at this commit
- "Roll back to this commit" action (one-click redeploy of a previous image)
- SHA/latest comparison indicator ("up to date" / "ahead by N commits" / "behind by N commits")
- Crash replay: playback controls (Play / Replay / Pause), speed control (1× / 2× / 4×), time-scrubbed logs + code + error flash
- Scrub file history (drag through versions of the suspect file)
- AI analysis with inline citations `[1]`, `[2]`, `[3]` etc.
- Receipts panel with click-to-jump evidence list
- Log persistence with hybrid retention (30 days OR last 5 deployments, whichever keeps more)

### Out of scope — cut intentionally
- "Open PR with guard restored" — requires GitHub write access; deferred indefinitely
- Dependency graph / blast radius — looks sad on small codebases
- Heatmap of crash-prone files — same
- Postmortem Markdown export — low demo value
- Recurrence detection — needs data volume that portfolio app won't have
- Self-analysis — recursion gag, not a feature

### Deferred to v2
- "Open PR with guard restored" (might return once GitHub write integration exists)
- Semantic log search
- Multi-app cross-correlation ("which apps crashed around the same time")

---

## 3. Architecture

### Service split

```
vector-platform/
├── vector-events/      ← shared module: event types, DTOs (no Docker image)
├── vector-ai/          ← shared module: AiProvider, Gemini impl (no Docker image)
├── vector-api/         ← existing backend, renamed
├── vector-analyzer/    ← NEW microservice, depends on vector-events + vector-ai
├── vector-web/         ← existing frontend, renamed
└── vector-updater/     ← existing
```

`vector-analyzer` is a Spring Boot service with:
- Its own Postgres schema (`analyzer`) on the shared Postgres instance
- Its own connection pool
- REST endpoints under `/api/analyzer/*`
- A `LISTEN launchpad_events` connection to receive real-time events from `vector-api` via Postgres NOTIFY (added in refactor Phase 5)
- Read-only GitHub API access for diffs, blame, and commit data (cached aggressively in its own schema)
- Dependency on `vector-ai` for LLM calls (Gemini by default)

### Data flow

**Normal state request** (user opens Time Machine for `docs-site`):
1. `vector-web` requests `/api/analyzer/apps/docs-site/timeline` from `vector-analyzer`
2. `vector-analyzer` queries its own `analyzer.timeline_event` table (populated by listening to `launchpad_events`)
3. Joins with GitHub commit data (cached in `analyzer.commit_cache`)
4. Returns timeline + stats + recent deploys list

**Commit detail request** (user clicks commit `9e41bd2`):
1. `vector-web` requests `/api/analyzer/apps/docs-site/commits/9e41bd2`
2. `vector-analyzer` returns:
   - Commit metadata (author, SHA, message) from cache
   - Logs from that commit's deployment window (from `analyzer.log_entry` table)
   - Diff (fetched from GitHub API on demand, cached)
   - File-at-commit contents (lazy-loaded when File tab is clicked)

**Rollback action** (user clicks "Roll back to this"):
1. `vector-web` posts to `vector-api` `/api/apps/docs-site/deployments` with `imageTag: "docs-site:9e41bd2"`
2. `vector-api` runs the normal deployment flow using the SHA-tagged image (already in DockerHub)
3. New deployment event fires, `vector-analyzer` sees it via LISTEN, timeline updates
4. User sees the rollback appear as a new green dot on the timeline in real time (via SSE)

**Crash state request** (user lands on Time Machine for a crashed app):
1. Same as normal state, plus additional panels
2. Analyzer identifies the suspect commit from the deployment-window correlation with the crash event
3. Analyzer fetches the diff between `crashed_commit` and `last_good_commit`
4. Analyzer calls `vector-ai` to generate the narrative with inline citations
5. Returns a `CrashAnalysis` object with `deterministic` + `aiNarration` sections

### Graceful degradation rules

- `vector-api` down → Time Machine renders from `vector-analyzer`'s own data. Shows "deployments unavailable" on action buttons (rollback disabled).
- `vector-analyzer` down → main dashboard works normally. Time Machine tab shows "Time Machine unavailable" banner instead of white-screening.
- Gemini API down / rate-limited → `AiNarration.available = false`, deterministic data still renders. Receipts still visible.
- GitHub API down → cached diff/blame data still served. Uncached requests show "diff unavailable."

---

## 4. Architectural decisions (locked)

Answers to the architectural questions that previously weren't in the plan. Execute against these; don't re-derive.

### 4.1 Authentication

**Decision:** `vector-analyzer` uses the same API key mechanism as `vector-api`.

- Both services read `VECTOR_API_KEY` from environment
- Both enforce it via an `ApiKeyAuthFilter` (copy the existing filter from `vector-api` into `vector-analyzer`; identical logic)
- `vector-web` sends `X-API-Key` header on every analyzer request, just as it does today for api requests
- If `vector-analyzer` ever calls `vector-api` (e.g., during a rollback orchestration), it sends the same key

No new users, no per-service keys, no JWT. Single shared secret.

Rationale: matches existing architecture, zero new infrastructure, good enough for single-user platform. If the product ever becomes multi-user, switch to per-user tokens in `user_account` (already in the schema — audit finding #20).

### 4.2 Next.js proxy layer

**Decision:** every analyzer endpoint consumed by the browser must have a matching Next.js proxy route in `vector-web/src/app/api/analyzer/*`.

Analyzer endpoints NEVER called directly from the browser — the browser has no access to `VECTOR_API_KEY`, only the Next.js server side does.

**Existing proxy routes to REUSE** (verified in `CONTEXT_CHECK.md` §7.5 — do NOT recreate):
- `/api/apps/[appName]/rollback` — existing, reused for rollback button (see §4.5)
- `/api/apps/[appName]/unpin` — existing, reused for the post-rollback unpin action
- `/api/apps/[appName]/events` — existing, can be reused for deployment history
- `/api/apps/[appName]/commits-ahead` — existing, reused for SHA/latest comparison indicator
- `/api/apps/[appName]/logs/runtime` — existing SSE log stream (live container logs, different from Time Machine's historical logs)

**New proxy routes to CREATE** (9 total, down from 10 in original plan since rollback is reused):

```
vector-web/src/app/api/analyzer/
├── apps/[appName]/timeline/route.ts              (Phase 3)
├── apps/[appName]/stats/route.ts                 (Phase 3)
├── apps/[appName]/deploys/route.ts               (Phase 3 — interleaved commit+deploy list)
├── apps/[appName]/commits/[sha]/route.ts         (Phase 4)
├── apps/[appName]/commits/[sha]/logs/route.ts    (Phase 4)
├── apps/[appName]/commits/[sha]/diff/route.ts    (Phase 4)
├── apps/[appName]/commits/[sha]/files/route.ts   (Phase 4, accepts ?path=)
├── apps/[appName]/crashes/[crashId]/route.ts     (Phase 5)
└── apps/[appName]/stream/route.ts                (Phase 5, SSE forwarding for analysis events)
```

Each new proxy route: read API key server-side, add `X-API-Key` header, forward request to `vector-analyzer`, stream response back. SSE routes need `AbortSignal` composition (per audit Finding #27) — `AbortSignal.any([req.signal, AbortSignal.timeout(10_000)])`.

Pattern identical to existing `/api/apps/[appName]/logs/runtime/route.ts`. Copy that file as the template.

### 4.3 Docker socket ownership

**Decision:** `vector-api` owns the Docker socket. `vector-analyzer` does NOT mount it.

For log tailing (needed for log persistence in Phase 4), `vector-api` exposes a new internal endpoint:

```
GET /api/internal/apps/{appName}/logs/stream    (SSE, API-key authenticated)
```

This streams live Docker logs. `vector-analyzer` consumes this endpoint via SSE client on startup for each running container and writes received lines to `analyzer.log_entry`.

Rationale: minimizes privileged-socket exposure (only one service has it), keeps `vector-api` as the sole Docker owner (simpler mental model, easier to reason about failure modes), and the extra network hop inside the Docker Compose network is negligible.

One downside: if `vector-api` is down, log tailing in `vector-analyzer` pauses. That's acceptable — `vector-analyzer` can retry the connection when `vector-api` comes back, and the gap gets tolerated by the retention policy.

### 4.4 SSE stream architecture

**Decision:** two SSE streams, each with a distinct responsibility.

1. **Events stream** (`vector-api`, existing, post-refactor): `GET /api/events/stream` — platform-wide event fan-out (deploys, crashes, uptime changes). Consumed by the main dashboard AND by Time Machine's timeline for live updates.

2. **Analysis stream** (`vector-analyzer`, new): `GET /api/analyzer/apps/{appName}/stream` — analyzer-specific updates (crash analysis started, completed, log tail lines, etc.). Only Time Machine consumes this.

The frontend opens both when on the Time Machine page. Each stream is multiplexed across all events (no per-crash SSE); filtering happens client-side by app name.

Rationale: clear separation of concerns. Platform events live in `vector-api`; analyzer-specific updates live in `vector-analyzer`. No ambiguity about which service owns a given event type.

### 4.5 Rollback behavior

**Decision:** consume the existing rollback endpoint in `vector-api`. Do NOT build a new one.

**What already exists** (per `CONTEXT_CHECK.md` §1.1):
- `POST /api/apps/{appName}/rollback` at `DeploymentController.java:100-117`
- Accepts `{ "eventId": <Long> }` — an ID referencing a prior `DEPLOY_FINISHED` or `MANUAL_ROLLBACK` event (NOT a commit SHA)
- Validates the target event type, re-deploys using the event's stored `imageName`
- **Auto-pins** the image post-rollback (side effect at `DeploymentServiceImpl.java:186`)
- Uses **current env vars**, not historical (env vars aren't snapshotted per-deployment)
- `TriggerSource.ROLLBACK` already exists in the enum
- Frontend proxy already exists at `frontend/src/app/api/apps/[appName]/rollback/route.ts`
- `ActionLockService` 5-second lock applies; concurrent rollback attempts receive `409 Conflict`

**What's missing** (to be added in refactor Phase 4):
- `rollback_from_sha` column on `deployment_event` — populated with the currently-running commit SHA when a rollback is recorded (for Time Machine timeline display)

**Time Machine UI flow:**
1. User clicks "Roll back to this" on a commit in the Recent Deploys list
2. Frontend finds the `DEPLOY_FINISHED` event ID for that commit (from the event history already fetched)
3. **Pre-confirm modal** (new, required due to auto-pin side effect):
   > Rolling back to commit `9e41bd2` will also **pin this app** to that image. Future pushes to the deployed branch won't auto-deploy until you unpin the app.
   >
   > Continue? [Cancel] [Roll back and pin]
4. On confirm: POST to existing `/api/apps/[appName]/rollback` proxy with `{ eventId }`
5. On success (202/200): refresh timeline, show persistent banner at top of Time Machine:
   > 📌 **App is pinned to commit 9e41bd2.** New commits won't auto-deploy. [Unpin]
6. Clicking [Unpin] calls existing `POST /api/apps/{appName}/unpin`
7. On 409 Conflict: show toast "An operation is in progress for this app. Try again shortly."
8. On 404 (deployment event no longer exists, e.g. app was hard-deleted): show error state

**Button state logic:**
- Disabled if current running commit == target commit (tooltip: "already running this version")
- Disabled if the target event is soft-deleted or archived
- Disabled during an in-flight rollback for the same app (use SWR revalidation or optimistic disable)

**Timeline visual for rollback events:**
- Rollback-triggered deploys show as a green deploy dot with a small ↺ badge
- Tooltip shows: *"Rolled back from {previous_sha} to {target_sha}"* (using `rollback_from_sha` column)

**CI pipeline prerequisite** (document in the README, not in the plan execution):
- Rollback requires CI to push immutable image tags (e.g., `{appName}:{sha}`). `latest`-only pipelines cannot roll back because the tag for a past event may have been overwritten.
- `CONTEXT_CHECK.md` §1.7 confirmed Launchpad is tag-agnostic — it stores whatever tag the webhook provided. Users must configure their CI to push SHA-tagged images for rollback to work reliably.

**Image existence is NOT pre-validated** (per §1.8 of the audit). Rollback fails mid-flight if the image isn't pullable, with the error surfaced in the event log. Acceptable for MVP; a pre-flight image check is a follow-up.

### 4.5.1 Note: Schema addition is in refactor Phase 4

The `rollback_from_sha` column belongs in the refactor's V14 migration, not in Time Machine's Phase 1. See `REFACTOR_PLAN.md` Phase 4. This Time Machine plan depends on that column existing before Phase 4 here.

### 4.6 Suspect-identification rules (MVP)

**Decision:** use a simple deterministic rule. No ML, no heuristics beyond what's in the deployment record.

When a CRASHED event arrives, the analyzer determines:

- **Suspect commit** = the `commit_sha` of the deployment that was RUNNING when the crash occurred. Always. Even if the deployment happened weeks ago.
- **Last good commit** = the most recent DEPLOY_FINISHED event's `commit_sha` before the current deployment. If no prior deployment exists, `last_good_commit_sha = null`.

Additional context the analyzer computes and includes:
- **Time since deploy** — `crash_time - last_deployed_at`. If < 1 hour: label "crash shortly after deploy" (strong signal). If > 24 hours: "crash long after deploy" (suggests a non-deploy-induced cause).
- **Crash count for this commit** — how many times has this SHA crashed? If >1: "recurring crash under this version."

The AI narration (Phase 6) receives these signals in its prompt and uses them to calibrate confidence. A crash 2 weeks after the last deploy should produce a narration that says something like "unlikely to be caused by deploy X" rather than blaming the deploy.

UI language matters:
- Badge says "SUSPECT" (not "cause"), reinforcing uncertainty
- AI narration must say "likely" / "possibly" / "appears to be" — never "the cause was"
- For crashes long after the last deploy, the panel should show a prominent note: *"Last deploy was N days ago. This crash may be unrelated to recent code changes."*

### 4.7 Historical backfill on first startup

**Decision:** backfill timeline events, do NOT attempt to generate analyses for past crashes.

On first startup (detected by `analyzer.timeline_event` being empty):

1. **Timeline event backfill:** read all rows from `public.deployment_event`, map to `analyzer.timeline_event` entries. Fast — one SQL query, possibly batched by app.
2. **Commit data backfill:** for each distinct `commit_sha` in the events, fetch commit metadata from GitHub and populate `analyzer.commit_cache`. Rate-limited (100 calls/minute max) to avoid hammering GitHub. Runs as a background job; doesn't block the service from starting.
3. **Historical crashes:** do NOT generate `crash_analysis` rows for past CRASHED events. The logs from those deployments don't exist; a crash analysis without logs is nearly worthless. Past crashes appear on the timeline as red dots but have no associated analysis. UI shows: *"Analysis unavailable — this crash occurred before log retention was enabled."*
4. **Historical logs:** impossible to backfill. Logs only exist for new deployments from this point forward.

The user sees: a timeline with all historical events represented, but drilling into past crashes (pre-Time-Machine) gives limited data. Any crash from this moment forward gets full analysis.

Subsequent startups skip the backfill (check `timeline_event` non-empty), only running the incremental event sync from the last processed event ID.

### 4.8 Coexistence with existing drawer log view

**Decision:** coexist. Each has a distinct job.

- **Existing drawer log view** (`vector-web` app detail drawer) = **live** logs only. Real-time Docker stream via SSE. For "what's happening right now." Keep it.
- **Time Machine commit-detail logs tab** = **historical** logs for a specific deployment window. For "what was happening when commit X was running." New feature.

Both read from different sources:
- Drawer → live Docker stream via `vector-api`
- Time Machine → `analyzer.log_entry` persisted history

No user confusion because they're in different UI contexts: drawer is "current state of an app," Time Machine is "historical view of an app."

The drawer's "Analyze with AI" button (current feature) becomes a link to Time Machine's current-state view (since current state = most recent commit with current logs). Don't duplicate the AI analysis — keep it only in Time Machine.

### 4.9 Auxiliary decisions

**Suspect file identification:** when an AI narration is generated, parse the stack trace to find the top frame pointing to code in the user's repo. That's the suspect file. If stack trace doesn't exist (e.g., a non-JVM app), fall back to the files changed in the suspect commit — if only one changed, that's the suspect; if many, no file is singled out.

**Avg build time metric:** add a `build_duration_ms` column to `deployment` table in the refactor's V14 migration. Populate it when a deployment transitions to RUNNING from PENDING. The summary card in Time Machine queries the avg over last 30 days.

**Environment variable list** for `vector-analyzer`:
```
VECTOR_API_KEY                      (shared with vector-api)
VECTOR_DB_URL                       (same Postgres instance)
VECTOR_DB_USER                      (dedicated user: vector_analyzer)
VECTOR_DB_PASS
VECTOR_GITHUB_TOKEN                 (read-only PAT)
VECTOR_GEMINI_API_KEY               (from vector-ai module)
VECTOR_API_INTERNAL_URL             (http://vector-api:8080 inside Docker network)
VECTOR_LOG_RETENTION_DAYS           (default 30)
VECTOR_LOG_RETENTION_DEPLOYS        (default 5)
```

**Postgres user setup:** add to the Postgres init script (runs once on container creation):
```sql
CREATE USER vector_analyzer WITH PASSWORD 'xxx';
GRANT CONNECT ON DATABASE vector TO vector_analyzer;
GRANT USAGE ON SCHEMA public TO vector_analyzer;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO vector_analyzer;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO vector_analyzer;
-- vector_analyzer creates its own schema via Flyway:
GRANT CREATE ON DATABASE vector TO vector_analyzer;
```

Flyway migrations run as `vector_analyzer`; the first migration creates the `analyzer` schema and subsequent tables within it.

**URL structure:**
- `/apps/{name}/time-machine` — normal state view, default date range (last 2 weeks)
- `/apps/{name}/time-machine?commit={sha}` — normal view with specific commit detail open
- `/apps/{name}/time-machine?crash={crashId}` — crash state view for specific crash
- Deep links from emails always use the crash form

**Sidebar entry:** yes, top-level "Time Machine" sidebar item. Clicking it shows an app picker if no app is currently selected, or goes to the most-recently-viewed app's Time Machine if there is one (stored in localStorage).

**Observability for `vector-analyzer`:**
- Same Logback config as `vector-api` (copy it)
- `/actuator/health` endpoint (Spring Boot Actuator dependency)
- Simple metrics exposed at `/actuator/metrics`: events processed, logs captured per minute, AI calls made, AI call failures, cache hit rates
- No external metrics backend (Prometheus etc.) — just the endpoints exist for future use

**Testing approach (portfolio-level, not production-grade):**
- Manual crash simulation: a dedicated test endpoint in `vector-api` that kills a specified container (behind API key, not exposed in production builds)
- LISTEN/NOTIFY integration: test locally by running both services + Postgres in Docker Compose, inserting a row directly with `psql`, verifying the event propagates
- AI prompt output: use a sandboxed Gemini API key with a small quota just for development; never burn production quota during prompt iteration
- Phase 6 includes a small set of golden (log, diff, expected-narration-shape) triples used to verify the prompt produces well-structured output across restarts

**Performance budgets:**
- Timeline with 500 events: initial render < 200ms on desktop
- Log playback at 4× speed: maintains ≥ 30 fps on 2019+ hardware
- AI analysis end-to-end (crash event → narration rendered): < 10s p95
- Commit detail open: logs + diff both loaded < 1.5s (cache hit) or < 5s (cache miss)

**Mobile/responsive scope:** Time Machine is **desktop-first**. Minimum width 1024px. Below that, show a "best viewed on desktop" notice. A native iOS app is the long-term mobile story (see `POST_REFACTOR_ROADMAP.md` §7), not a responsive mobile web view.

**Accessibility (portfolio-level):** ARIA roles on the timeline (role="slider"), keyboard navigation for the scrubber (arrow keys), focus-visible rings on all interactive elements, status dots have text alternatives. Screen-reader testing not done; skip that bar.

### 4.10 Existing platform behaviors Time Machine must respect

The context check (`CONTEXT_CHECK.md` §12) surfaced three platform behaviors that weren't in the original plans. Each affects Time Machine UX and is now accounted for here so the implementation doesn't break existing functionality.

**Pin/unpin mechanic:**
- Apps have a `pinned_image` field. When set, webhooks for that app return `UPDATE_AVAILABLE` events instead of auto-deploying.
- Rollback **auto-pins** as a side effect — see §4.5 for the required pre-confirm modal and post-rollback banner.
- Time Machine UI additions:
  - "Pinned" badge on the Time Machine header when `deployment.pinned_image != null`. Clicking shows pin details and an Unpin action.
  - `UPDATE_AVAILABLE` events appear on the timeline as small yellow dots with label "update available" — tells the user "a new commit arrived but you're pinned."
  - Existing `POST /api/apps/{appName}/unpin` proxy is used for unpin actions.

**ActionLockService (5-second in-memory lock):**
- All mutating endpoints (rollback, restart, stop, subdomain, deploy) acquire a per-app lock. Concurrent requests receive `409 Conflict`.
- Time Machine UI must handle 409 gracefully for every mutating action:
  - Rollback button → toast: "An operation is already in progress for this app. Try again shortly."
  - Any other future action the UI adds must follow the same pattern.
- Do NOT attempt to serialize on the frontend; the backend's lock is the source of truth.

**Soft-delete with 5-minute restore window:**
- Deleted apps stay in the DB for 5 minutes with `deleted_at` set, then hard-delete.
- During this window, `GET /api/apps/{appName}` returns the app; `GET /api/apps` excludes soft-deleted apps from the main list.
- `POST /api/apps/{appName}/restore` reverses the delete during the window.
- **Decision for MVP (per decision N1 below):** Time Machine shows a 404 for soft-deleted apps. Restore action remains in the main dashboard. Rationale: adding an archive view adds complexity without meaningful demo value; the restore window is a rare edge case.
- Future: if a user deep-links to a Time Machine URL for a soft-deleted app, the 404 page could show "This app was deleted. Restore from the main dashboard" — minor polish, not required for MVP.

---

## 5. Database schema

All `vector-analyzer` tables live in their own Postgres schema called `analyzer` (created by the first analyzer migration). `vector-api` has no permission to write to this schema; `vector-analyzer` has read permission on `vector-api`'s tables where needed.

### V1 — schema setup and core tables

```sql
CREATE SCHEMA IF NOT EXISTS analyzer;

-- Persistent log storage with hybrid retention
CREATE TABLE analyzer.log_entry (
    id           BIGSERIAL PRIMARY KEY,
    app_name     VARCHAR(100) NOT NULL,
    deployment_id BIGINT NOT NULL,       -- joins to public.deployment.id
    commit_sha   VARCHAR(40),            -- denormalized for query simplicity
    timestamp    TIMESTAMP NOT NULL,
    stream       VARCHAR(8) NOT NULL,    -- 'stdout' | 'stderr'
    line         TEXT NOT NULL
);
CREATE INDEX idx_log_app_time ON analyzer.log_entry (app_name, timestamp DESC);
CREATE INDEX idx_log_deployment ON analyzer.log_entry (deployment_id);
CREATE INDEX idx_log_commit ON analyzer.log_entry (commit_sha) WHERE commit_sha IS NOT NULL;

-- GitHub API cache (commits, diffs, blame)
CREATE TABLE analyzer.commit_cache (
    repo_full_name VARCHAR(200) NOT NULL,
    sha            VARCHAR(40) NOT NULL,
    author         VARCHAR(200),
    authored_at    TIMESTAMP,
    message        TEXT,
    PRIMARY KEY (repo_full_name, sha)
);

CREATE TABLE analyzer.diff_cache (
    repo_full_name VARCHAR(200) NOT NULL,
    base_sha       VARCHAR(40) NOT NULL,
    head_sha       VARCHAR(40) NOT NULL,
    diff_json      TEXT NOT NULL,       -- GitHub compare API response
    cached_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (repo_full_name, base_sha, head_sha)
);

-- Crash analyses (one per crash event; regenerable)
CREATE TABLE analyzer.crash_analysis (
    id                  BIGSERIAL PRIMARY KEY,
    app_name            VARCHAR(100) NOT NULL,
    crash_event_id      BIGINT NOT NULL,   -- joins to public.deployment_event.id
    suspect_commit_sha  VARCHAR(40),
    last_good_commit_sha VARCHAR(40),
    ai_narration        TEXT,               -- markdown with [N] citations
    ai_provider_used    VARCHAR(50),        -- 'gemini-2.5-flash', 'ollama-llama3', etc.
    evidence_json       TEXT NOT NULL,      -- array of {id, type, source, content}
    generated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (crash_event_id)
);
CREATE INDEX idx_crash_analysis_app ON analyzer.crash_analysis (app_name, generated_at DESC);

-- Timeline event projection (read-optimized for the timeline view)
-- Populated by listening to launchpad_events NOTIFY channel
CREATE TABLE analyzer.timeline_event (
    id           BIGSERIAL PRIMARY KEY,
    app_name     VARCHAR(100) NOT NULL,
    event_type   VARCHAR(50) NOT NULL,   -- 'DEPLOY' | 'COMMIT' | 'CRASH' | 'RESTART' | 'SUSPECT'
    commit_sha   VARCHAR(40),
    occurred_at  TIMESTAMP NOT NULL,
    metadata_json TEXT                    -- extra per-type data
);
CREATE INDEX idx_timeline_app_time ON analyzer.timeline_event (app_name, occurred_at DESC);
```

### Retention policy

Scheduled job in `vector-analyzer` runs every 6 hours:

```sql
-- Keep logs from last 30 days OR last 5 deployments per app, whichever keeps more
WITH recent_deployments AS (
    SELECT DISTINCT ON (app_name) id
    FROM public.deployment
    WHERE deleted_at IS NULL
    ORDER BY app_name, last_deployed_at DESC
    LIMIT 5
)
DELETE FROM analyzer.log_entry
WHERE timestamp < NOW() - INTERVAL '30 days'
  AND deployment_id NOT IN (SELECT id FROM recent_deployments);

-- Clean diff cache older than 7 days
DELETE FROM analyzer.diff_cache WHERE cached_at < NOW() - INTERVAL '7 days';
```

---

## 6. Phased implementation

Seven phases. Execute in order. Each phase is a feature branch, merged to `dev` before starting the next.

### Phase 0 — Prerequisites check

Not code — a confirmation gate. Before starting Phase 1, verify:

- [ ] Refactor Phase 5 is merged (shared `vector-events` module exists, `pg_notify` trigger is live, `deployed_at` column exists on `deployment` table)
- [ ] Vector rename and monorepo restructure are complete (`vector-api`, `vector-web`, etc. are the real folder names)
- [ ] `AiProvider` interface and `GeminiProvider` exist in `vector-ai` module (see `POST_REFACTOR_ROADMAP.md`)
- [ ] GitHub PAT is available as an env var (`VECTOR_GITHUB_TOKEN`) and has read access to the deployed repos

If any of these are false, do not start Phase 1. Fix the prerequisite first.

### Phase 1 — Skeleton service

**Goal:** `vector-analyzer` starts up, connects to Postgres as its own user, exposes a health endpoint, enforces the shared API key, gets picked up by docker-compose.

**Changes:**
- Create `vector-analyzer/` folder with a minimal Spring Boot project
- `pom.xml` depends on `vector-events`, `vector-ai`, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`, `spring-boot-starter-security`, `postgresql`, `flyway-core`
- Copy `ApiKeyAuthFilter` from `vector-api` (same logic, same `VECTOR_API_KEY` env var — see §4.1)
- `SecurityConfig` enforcing API key on all `/api/analyzer/**` endpoints; permit `/actuator/health`
- Flyway config pointing to `classpath:db/migration/analyzer` with `schemas=analyzer`
- First migration creates the schema and a trivial `analyzer_health` table
- `/api/analyzer/health` endpoint (API-key protected) and `/actuator/health` (unprotected, for Docker healthcheck)
- Postgres init script addition (one-time, in the Postgres container's `/docker-entrypoint-initdb.d/`): creates `vector_analyzer` user with SELECT-on-public, CREATE-on-database permissions per §4.9
- Environment variables per §4.9 env var list
- Dockerfile (multi-stage, same pattern as `vector-api`)
- Add service to `docker-compose.yml`: port 8082, Traefik labels to route `/api/analyzer/*` and `/api/analyzer/**/stream` (with `flushinterval=100ms` for SSE paths), `depends_on: postgres: condition: service_healthy`, resource limit ~512MB memory
- Copy Logback config from `vector-api`

**Verification:**
- `docker compose up` brings up `vector-analyzer` alongside existing services
- `curl http://localhost/api/analyzer/health` without API key returns 401
- `curl http://localhost/api/analyzer/health -H "X-API-Key: $VECTOR_API_KEY"` returns OK
- `curl http://localhost:8082/actuator/health` (direct, bypassing Traefik) returns UP
- Flyway migration ran (check `analyzer.flyway_schema_history`)
- Postgres: `\du` shows `vector_analyzer` user; `\dn` shows `analyzer` schema owned by `vector_analyzer`

**Effort:** ~4 hours. Single PR.

### Phase 2 — Event consumption and timeline projection

**Goal:** `vector-analyzer` listens to `launchpad_events` NOTIFY channel, populates its own `timeline_event` table, and serves the basic timeline for one app.

**Changes:**
- `EventListener` bean that opens a persistent Postgres `LISTEN launchpad_events` connection on startup
- On each NOTIFY payload, map to a `TimelineEvent` row and insert into `analyzer.timeline_event`
- **First-startup backfill** (per §4.7): on startup, if `analyzer.timeline_event` is empty, backfill ALL rows from `public.deployment_event` (not just the last processed one). Fast, single query.
- **Subsequent-startup catch-up**: if `timeline_event` is non-empty, sync from the last processed event ID forward. Handles the "analyzer was down briefly" case.
- **LISTEN connection health**: check the connection every 30s; reconnect if dropped. On reconnect, run the catch-up query to fill any gap.
- Core migration V2 adds `analyzer.timeline_event` table
- REST endpoint `GET /api/analyzer/apps/{appName}/timeline?from=...&to=...` returns timeline events for the app in the given range
- Also fetch commit events: on startup and every 5 minutes, sync recent commits from GitHub for each deployed repo. Write COMMIT events into `timeline_event`. Rate-limited to 100 GitHub calls/min to avoid hammering during initial backfill.

**Verification:**
- Fresh `analyzer` schema, start the service → all historical `deployment_event` rows appear as `timeline_event` rows
- Deploy an app via the normal flow → a new DEPLOY event appears in `analyzer.timeline_event` within a second
- Kill `vector-analyzer`, deploy another app, restart `vector-analyzer` → the missed event is backfilled
- Simulate LISTEN connection drop (restart Postgres with `docker compose restart postgres`) → analyzer reconnects and resyncs
- `GET /api/analyzer/apps/docs-site/timeline?from=2026-04-01` returns events

**Effort:** ~2 days. The Postgres LISTEN + startup backfill + reconnect logic has real subtlety — pay attention to cursor tracking and race conditions during reconnect.

### Phase 3 — Frontend Time Machine shell (normal state)

**Goal:** The Time Machine UI renders for any app, showing the timeline and top summary cards. No crash state yet, no commit detail view.

**Changes in `vector-web`:**
- New route: `/apps/[appName]/time-machine`
- "Time Machine" link added to the app detail drawer
- New top-level "Time Machine" sidebar entry (per §4.9): clicking with no app selected shows an app picker; with one selected (or one remembered in localStorage), routes to its Time Machine
- New page component with:
  - Header: app name dropdown, status, "Open app" button
  - Four summary cards: uptime 30d, deploys 30d, last crash, avg build
  - Timeline component (horizontal scrollable with scrub bar, hour/day/week zoom)
  - Empty state for the commit-detail area ("select a commit to see details")
- Next.js proxy routes (per §4.2): `api/analyzer/apps/[appName]/timeline/route.ts`, `api/analyzer/apps/[appName]/stats/route.ts`, `api/analyzer/apps/[appName]/deploys/route.ts`. Copy the existing log-stream proxy as template; all three forward with `X-API-Key` header.
- Data hooks: `useTimeline(appName, range)`, `useAppStats(appName)`, `useRecentDeploys(appName)` hitting the proxy routes
- Summary cards: uptime/deploys count come from existing `vector-api` endpoints; last-crash and avg-build come from new analyzer endpoints (avg build requires the `build_duration_ms` column per decision D6)

**Verification:**
- Open Time Machine for any running app → timeline renders with real deploy/commit events
- Zoom between hour/day/week changes the visible range
- Summary cards show correct data
- Browser network tab shows requests going to `/api/analyzer/*` (Next.js proxy) not directly to `vector-analyzer`
- API key is never visible in browser devtools

**Effort:** ~3 days. Timeline component is the bulk of it.

### Phase 4 — Commit detail view

**Goal:** Click any commit in the timeline or recent deploys list → see the commit detail view with Logs / Diff / File tabs.

**Changes:**

Backend (`vector-api`) — one additive change:
- New internal endpoint `GET /api/internal/apps/{appName}/logs/stream` (SSE, API-key authenticated) that streams live Docker logs for a given running container. This is what `vector-analyzer` consumes for log persistence. See §4.3.
- **Rollback endpoint is NOT new.** The existing `POST /api/apps/{appName}/rollback` is used as-is. See §4.5 for how Time Machine consumes it.

Backend (`vector-analyzer`):
- `GET /api/analyzer/apps/{appName}/commits/{sha}` returns commit metadata + the deployment ID(s) where this SHA ran
- `GET /api/analyzer/apps/{appName}/commits/{sha}/logs` returns log entries for the deployment window
- `GET /api/analyzer/apps/{appName}/commits/{sha}/diff` returns the diff against the previous commit (from GitHub API, cached)
- `GET /api/analyzer/apps/{appName}/commits/{sha}/files?path=...` returns file contents at that SHA (from GitHub API, cached)

Log persistence (new in this phase):
- `LogTailService` in `vector-analyzer`: on startup and whenever a DEPLOY_FINISHED event arrives, connects to `vector-api`'s internal SSE log stream for each running container. See §4.3 for why the analyzer doesn't touch the Docker socket directly.
- Each tail runs as a managed SSE client; received lines are buffered and batch-inserted into `analyzer.log_entry` (batch of 100 lines or 2-second flush, whichever first — see §9 risk #1)
- Deployment ID comes from the current deployment for that app; SHA comes from deployment's `commit_sha`
- Reconnect with exponential backoff if `vector-api` is unreachable
- Scheduled retention job runs every 6 hours (see §5 retention policy)

Frontend:
- Commit detail component with tab navigation (Logs / Diff / File)
- Logs tab: virtualized log viewer — confirmed needed per `CONTEXT_CHECK.md` §4.4 (existing `useBuildLogs.ts` has no virtualization; 5000-line ring buffer only)
- Diff tab: unified diff renderer via `react-diff-view`
- File at this commit tab: lazy-loaded, syntax-highlighted view via `shiki` or `react-syntax-highlighter`
- **"Roll back to this" button** — the full flow per §4.5:
  - Identifies the target event ID (from the already-fetched deploys list)
  - Shows pre-confirm modal explaining the auto-pin side effect
  - Calls existing `/api/apps/[appName]/rollback` proxy with `{ eventId }`
  - Shows post-rollback pin banner with Unpin action (uses existing `/api/apps/[appName]/unpin` proxy)
  - Handles 409 Conflict with toast
  - Handles 404 gracefully
  - Disabled if current running commit == target commit
- Recent Deploys list beneath the timeline (interleaved commits + deploys as in mockup). Events where `trigger_source = ROLLBACK` get a small ↺ badge; hover shows "Rolled back from {rollback_from_sha}"
- `UPDATE_AVAILABLE` events (from pinned apps receiving pushes) shown on timeline as small yellow dots with "update available" label — see §4.10
- Additional Next.js proxy routes: `commits/[sha]/*` handlers (4 new routes). **No new `rollback` proxy** — the existing one is reused.

**Verification:**
- Click a commit → logs for that window appear
- Click an older commit whose logs have been cleaned → "logs no longer available — retention expired"
- Diff renders with colored +/- lines
- File tab loads file contents
- "Roll back to this" → pre-confirm modal explains auto-pin → confirm → deployment starts → pin banner appears
- Click Unpin on the banner → banner disappears, pinned badge on header disappears
- Attempt rollback while another rollback is in progress → 409 toast appears
- Attempt rollback to current commit → button disabled with tooltip
- Push a new commit while an app is pinned → `UPDATE_AVAILABLE` event appears on timeline as yellow dot
- SHA/latest comparison indicator shows correctly ("up to date" / "ahead by N commits")
- Kill `vector-api` while log tail is active → analyzer logs errors but stays up; when vector-api returns, tails reconnect

**Effort:** ~3 days (down from ~4 — rollback backend work cut since endpoint exists). Log persistence is now the heaviest part; rollback UX (pre-confirm modal, post-rollback banner, pin handling) is ~half a day.

### Phase 5 — Crash state foundation

**Goal:** When the user navigates to Time Machine for a crashed app, the crash panels render with deterministic data (no AI yet). Playback controls work.

**Changes:**

Backend (`vector-analyzer`):
- When a CRASHED event arrives (via NOTIFY), auto-generate a `crash_analysis` row per the rules in §4.6:
  - `suspect_commit_sha` = commit SHA of the deployment that was RUNNING when the crash occurred (via `last_deployed_at` correlation)
  - `last_good_commit_sha` = commit SHA of the most recent prior DEPLOY_FINISHED event, or null
  - **Context signals** computed and stored in `evidence_json` metadata:
    - `time_since_deploy_minutes` — how long between the deploy and the crash
    - `crash_count_for_commit` — how many prior CRASHED events exist for this SHA (for recurrence signaling)
  - Suspect file determined from stack trace (top frame in user's repo) if available; else from files changed in suspect commit (only if single-file change)
  - `evidence_json` = array of {id, type, source, content} covering: log lines around crash, diff hunk from suspect commit, commit metadata
  - `ai_narration` = null (Phase 6 adds this)
- `GET /api/analyzer/apps/{appName}/crashes/{crashId}` returns the analysis

Frontend:
- When the user lands on `/apps/{name}/time-machine?crash={crashId}` (or if the app is currently crashed):
  - Show the crash-state layout: logs panel (playback), code panel (suspect file), scrub file history, AI Analysis placeholder, Receipts panel
  - If `time_since_deploy_minutes > 1440` (24h), show a prominent note above analysis: *"Last deploy was N days ago. This crash may be unrelated to recent code changes."*
  - If `crash_count_for_commit > 1`, show a badge: *"Recurring — crashed N times under this version"*
- Logs panel: renders log entries with timestamps, supports playback
  - State: single `currentTime` variable; every panel subscribes (per §9 risk #4)
  - Play button advances `currentTime` at real speed × `playbackSpeed`
  - Red flash animation when `currentTime` crosses the error log line's timestamp
  - Pause/Replay/Speed controls (1× / 2× / 4×)
- Code panel: shows suspect file at the suspect commit, highlights the line referenced in the stack trace (or, if no stack trace, no highlight — just shows the file)
- Scrub file history: slider showing last N commits that touched the suspect file (consumes `commits/{sha}/history` endpoint — new in this phase)
- AI Analysis panel: shows "Analysis in progress..." or cached analysis if present
- Receipts panel: renders evidence from `evidence_json`
- New proxy routes: `api/analyzer/apps/[appName]/crashes/[crashId]/route.ts`, `api/analyzer/apps/[appName]/stream/route.ts` (SSE forwarding for analysis-complete events)

**Verification:**
- Crash a test container intentionally → Time Machine crash-state UI appears for that app
- Crash an app that's been running stable for > 24h → "crash may be unrelated" note appears
- Crash the same commit twice → "recurring" badge appears on second crash
- Playback scrubs logs correctly; red flash fires at the right moment
- Suspect file displays with highlighted line (when stack trace contains user code)
- App with no stack trace in logs → code panel shows suspect commit's changed files, no line highlight
- Scrub file history pulls last N commits touching that file

**Effort:** ~5 days. The playback state machine is the trickiest part.

### Phase 6 — AI narration

**Goal:** Crash analysis gets populated with AI-generated explanation with inline citations.

**Depends on:** `vector-ai` shared module with `GeminiProvider` working (see `POST_REFACTOR_ROADMAP.md`)

**Changes:**

Backend:
- `AnalysisGeneratorService` in `vector-analyzer` builds a prompt from the crash evidence:
  - Stack trace
  - Log lines around the crash
  - Diff of suspect commit
  - Commit message
  - **Context signals** (per §4.6): `time_since_deploy_minutes`, `crash_count_for_commit`
- Calls `aiProvider.analyze(prompt)` (from `vector-ai`)
- Expects the response to include inline citation markers `[1]`, `[2]`, etc. corresponding to the evidence array
- Stores response in `crash_analysis.ai_narration` and `ai_provider_used`
- If AI call fails or times out (30s), records a failed narration state and returns deterministic data only. Persists the failure so retries don't repeat unless user-triggered.

Prompt design — important to get right:
- System prompt: describe the task, require citation markers, forbid confident cause-claims (require "likely" / "possibly" / "appears to be" — see §4.6 UI language), forbid suggesting specific code fixes (only "what likely happened")
- System prompt explicitly handles the time-since-deploy signal: if > 24h, narration must not blame the deploy as primary cause
- User prompt: structured evidence with each item tagged `[1]`, `[2]`, etc.
- Response expected in markdown with citations
- During development: use Gemini 2.5 Pro for prompt iteration (better reasoning), switch to 2.5 Flash for production (cheaper/faster)

Frontend:
- AI Analysis panel renders markdown with citation markers
- Click `[N]` → scrolls/highlights Receipt #N
- "Hide receipts" toggle works
- Graceful fallback: if narration unavailable, show "analysis unavailable" inline
- "Regenerate analysis" button when narration failed (limited to once per user per crash to prevent quota abuse)

**Verification:**
- Crash a test container → AI narration appears within ~5 seconds
- Citation markers are clickable and jump to receipts
- Kill Gemini API access (block it at firewall) → crash state still renders, narration section shows unavailable

**Effort:** ~3 days. Prompt iteration will take most of the time.

### Phase 7 — Polish and rough edges

**Goal:** Everything works cleanly. Details that were skipped in earlier phases get fixed.

**Changes:**
- Loading states everywhere (timeline loading, commit detail loading, file contents loading)
- Error states everywhere (graceful handling of every "X unavailable" case listed in §3)
- Empty states for apps with no deploys yet, no crashes yet
- Performance: ensure timeline with 500+ events still renders smoothly (virtualize if needed)
- Keyboard shortcuts: Space to play/pause, arrow keys to step through timeline, Esc to exit
- SHA/latest indicator polish (accurate detection of "ahead by N commits")
- Receipt hover effects, click-to-jump smooth scrolling
- Mobile-responsive layout (or explicit "desktop only" messaging if you don't want to support mobile)
- Frontend error boundaries per panel so one panel breaking doesn't kill Time Machine

**Verification:**
- Walk through every state manually: no crashes, with crashes, during a deploy, during a rollback, after AI failure
- Perf test with a mock app that has 1000 timeline events
- Keyboard shortcuts work

**Effort:** ~3 days.

---

## 7. Decisions checklist

Before starting execution, review these:

**Architecture decisions** (from §4 — already locked, listed for sign-off):
- [ ] **A1:** API key sharing between `vector-api` and `vector-analyzer` (single shared `VECTOR_API_KEY`)
- [ ] **A2:** Next.js proxy pattern for every analyzer endpoint
- [ ] **A3:** Docker socket stays only in `vector-api`; analyzer consumes log stream via internal API
- [ ] **A4:** Two SSE streams (events from api, analysis from analyzer)
- [ ] **A5:** Rollback = new deployment with SHA-tagged image + `TriggerSource.ROLLBACK`
- [ ] **A6:** Suspect = deployment running at crash time; UI calibrated for uncertainty
- [ ] **A7:** Backfill timeline events, not historical crash analyses
- [ ] **A8:** Drawer live logs coexist with Time Machine historical logs

**Implementation decisions** (detail-level):
- [ ] **D1 (Phase 4):** Log persistence granularity — every line (recommended)
- [ ] **D2 (Phase 4):** Diff renderer library — `react-diff-view` (recommended) or custom
- [ ] **D3 (Phase 5):** Playback speed options — 1× / 2× / 4× (recommended) or finer-grained
- [ ] **D4 (Phase 6):** Max AI timeout — 30 seconds (recommended)
- [ ] **D5 (Phase 6):** AI model for production — Gemini 2.5 Flash (recommended). Future fine-tuned model slots in via the same provider interface.
- [ ] **D6 (Phase 4):** Add `build_duration_ms` column to `deployment` in refactor V14 migration (for avg build time summary card)
- [ ] **D7 (Phase 4):** Add `rollback_from_sha` column to `deployment_event` in refactor V14 migration
- [ ] **D8 (Phase 4):** Add `ROLLBACK` value to `TriggerSource` enum in `vector-events`

---

## 8. Out-of-scope reminders

These are cut. If scope creep appears, say no:

- PR creation / GitHub write access
- Dependency graphs
- Heatmaps
- Postmortem export
- Self-analysis
- Recurrence detection
- Multi-app correlation
- Mobile app (separate project, separate course)
- Fine-tuned ML model (separate project, hooks in via AiProvider later)

---

## 9. Risks

**1. Log volume.** If any deployed app is log-chatty (thousands of lines per minute), `analyzer.log_entry` could grow fast. Retention keeps it bounded, but the *write* load might affect Postgres. Mitigation: batch log writes (insert every 100 lines or every 2 seconds, whichever comes first) instead of per-line inserts.

**2. GitHub API rate limits.** 5000 req/hr per token. A lot of caching mitigates this, but during initial backfill of commit history for multiple apps, you might hit limits. Mitigation: backfill asynchronously with a rate limiter, don't block startup.

**3. Postgres LISTEN reliability.** Long-lived LISTEN connections can drop silently. Mitigation: health check the connection every 30s and reconnect; rely on backfill on reconnect to fill any gap.

**4. Playback timing accuracy.** Synchronizing logs with the code panel at variable playback speeds is fiddly. Mitigation: base everything on a single monotonic `currentTime` state in the frontend; every panel subscribes to it.

**5. Prompt quality for the AI narration.** The quality of citations depends entirely on the prompt. Expect to iterate for a day or two until it reliably produces well-cited output. Mitigation: keep the prompt simple, use Gemini 2.5 Pro (not Flash) for initial prompt development, switch to Flash for production once prompt is stable.

---

## 10. Execution workflow

For each phase:

1. Branch: `feature/time-machine-phase-N` off `dev`
2. Come back to the planning chat and ask for the Claude Code prompt for that phase
3. Execute in Claude Code (Sonnet is fine for most; Opus for Phase 6's prompt iteration)
4. Verify via the phase's verification steps
5. Merge to `dev`, tag with phase name
6. Move to next phase

Do NOT execute two phases in parallel. The state machine for the crash view (Phase 5) especially needs focused attention.

---

## 11. What comes after

Once Time Machine is done:

- **v2 features to consider:** semantic log search, multi-app correlation, "blast radius" only if codebases grow large enough to justify it
- **Fine-tuned model integration:** replace or supplement Gemini with the locally-hosted fine-tune. Interface-compatible — just a new `AiProvider` impl.
- **iOS app:** consumes the same `vector-analyzer` REST endpoints to show crashes on the go.

Time Machine is designed to be extended, not rewritten.
