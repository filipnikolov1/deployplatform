# Plan amendments — post context check

**Date:** 2026-04-24
**Triggered by:** `CONTEXT_CHECK.md` audit — 52 assumptions verified, 22 EXISTS, 14 PARTIAL, 13 MISSING, 2 DIFFERENT, 1 UNABLE.

This document records every change applied across the four planning docs as a result of the context check. Plans have been updated in-place; this doc is the audit trail — what changed, why, and where.

---

## Headline impact

The audit caught a **meaningful amount of duplicated planning work**. 22 features I'd treated as "new to be built" already exist. Net effect: plans got **leaner**, not heavier. Estimated effort savings across all phases: ~3 days of work, mostly in refactor Phase 4 (migration shrinks) and Time Machine Phase 4 (frontend reuses existing rollback endpoint).

Three genuinely new things surfaced that weren't in any plan:

1. **Pin/unpin mechanic** — rollback auto-pins the target image. Webhooks for pinned apps return `UPDATE_AVAILABLE` instead of deploying. Time Machine's rollback UI must explain this to users.
2. **ActionLockService** — a 5-second in-memory lock on all mutating app endpoints. Time Machine's rollback flow must handle `409 Conflict` responses.
3. **Soft-delete with 5-minute restore window** — deleted apps appear in history during their grace period. Time Machine timeline must decide how to render these (dimmed? filtered out? with a "restore" action?).

None of these block the plans — but they are behaviors I didn't know about and that affect UX decisions. Each is now documented in the relevant plan section.

---

## 1. REFACTOR_PLAN.md — changes applied

### Phase 4 — V14 migration reductions

**Removed from migration (already exists):**
- `ALTER TABLE deployment ADD COLUMN commit_sha VARCHAR(40)` — column exists on `deployment.java:40` and `deployment_event.java:38`, both populated consistently.
- Adding `ROLLBACK` to `TriggerSource` enum — enum at `TriggerSource.java:3-8` already has all five values (`AUTOMATIC, MANUAL, ROLLBACK, RESTART, SELF_UPDATE`).

**Retained in migration (confirmed missing):**
- `last_deployed_at TIMESTAMP` on `deployment` — still missing; update logic in `DeploymentServiceImpl` to set it when status transitions to RUNNING.
- `rollback_from_sha VARCHAR(40)` on `deployment_event` — column doesn't exist. Populate it when recording a rollback-triggered deployment.
- Missing indexes (`idx_deployment_status_live`, `idx_event_app_type_created`) — confirmed missing.
- FK constraints across tables — confirmed missing.

**Refined scope:**
- `build_duration_ms` on `deployment` is an **aggregate** over existing per-event durations. `deployment_event.duration_ms` is already populated for BUILD_STARTED/BUILD_FINISHED and DEPLOY_STARTED/DEPLOY_FINISHED events (`DeploymentServiceImpl.java:83,89,143,149`). The migration adds the aggregate column and a backfill query:
  ```sql
  UPDATE deployment d SET build_duration_ms = (
    SELECT duration_ms FROM deployment_event
    WHERE app_name = d.app_name AND event_type = 'BUILD_FINISHED'
    ORDER BY created_at DESC LIMIT 1
  );
  ```

### Phase 3 — NotificationService expansion confirmed

- `NotificationService` interface truly has one method (`sendDownAlert`). No plan change.
- CRASHED event enum value exists at `DeploymentEventType.java:10` but is never emitted. UptimeMonitor needs to record it — confirmed. No plan change.
- Email sent today only on DOWN transition. All other plan-proposed triggers (deploy failed, crashed, recovered) confirmed missing.

### Phase 5 — Postgres NOTIFY confirmed needed

- No `pg_notify`, outbox, or LISTEN anywhere in the code. Confirmed as planned. No change.

### New awareness added to Phase 2 section

The audit surfaced **ActionLockService** (not previously in plans). This affects Phase 2's async webhook refactor:

- `ActionLockService` uses an in-memory 5-second lock on all mutating endpoints (restart, stop, rollback, subdomain, deploy). Webhooks that race during an in-flight deploy receive `409 Conflict`.
- The async webhook refactor (Phase 2) must preserve this lock. The controller acquires the lock before dispatching the async task; the task releases it when the Docker work completes or fails.
- This is now called out in `REFACTOR_PLAN.md` Phase 2 — "preserving ActionLockService semantics" as an explicit requirement.

---

## 2. TIME_MACHINE_PLAN.md — changes applied

### §4.5 Rollback behavior — rewritten

Rewrite rather than tweak, because the existing implementation differs meaningfully from the plan's assumptions:

- **Payload is `eventId`, not `commitSha`.** The `POST /api/apps/{appName}/rollback` endpoint at `DeploymentController.java:100-117` accepts `{ "eventId": <Long> }` referencing a prior `DEPLOY_FINISHED` or `MANUAL_ROLLBACK` event. The UI will fetch the event's `commit_sha` to display to the user but pass `eventId` to the endpoint.
- **Auto-pin is a post-rollback side effect.** After rollback, the app is pinned to the rolled-back image. Subsequent webhooks won't auto-deploy new commits until the app is unpinned via `POST /api/apps/{appName}/unpin`. The Time Machine rollback button must:
  - Before confirming: show a modal explaining "Rolling back will also pin this app. Future pushes to the deployed branch won't deploy until you unpin."
  - After rollback: show a persistent banner on the Time Machine page: *"App is pinned to commit X. New commits won't auto-deploy. [Unpin]"*
- **Env vars are current, not historical.** The rolled-back deployment uses the app's current env vars, not the env vars from when the target deployment originally ran. This is the correct behavior for most cases (users expect secrets to follow current state) but must be documented in the UI.
- **No new endpoint needed.** The plan's proposed new `vector-api` endpoint is cut. Time Machine consumes the existing one.
- **Proxy route already exists** at `frontend/src/app/api/apps/[appName]/rollback/route.ts`. No new proxy route needed either.
- **Image existence is not pre-validated.** Regular deploys fail mid-flight if the image isn't pullable. Plan should note this as a known rough edge — for portfolio MVP, fail mid-flight with a clear error in the event log is acceptable.
- **CI pipeline prerequisite:** Rollback only works if CI pushes immutable image tags (e.g., SHA-tagged). `latest`-only pipelines cannot be rolled back because the image tag for a past event may have been overwritten. Document this as a deployment prerequisite.

### §4.2 Proxy routes — updated list

The audit listed 27 existing proxy routes, several of which cover endpoints the plan assumed were new:

**Proxy routes for Time Machine that do NOT need to be created (they exist):**
- `/api/apps/[appName]/rollback` — existing, reuse for rollback button
- `/api/apps/[appName]/events` — existing, reuse for timeline event fetching
- `/api/apps/[appName]/commits-ahead` — existing, reuse for SHA/latest comparison indicator
- `/api/apps/[appName]/logs/runtime` — existing SSE log stream, reuse for live log panels

**Proxy routes for Time Machine that DO need to be created:**
- `/api/analyzer/apps/[appName]/timeline` — calls analyzer, new
- `/api/analyzer/apps/[appName]/stats` — calls analyzer, new
- `/api/analyzer/apps/[appName]/deploys` — calls analyzer, new (note: different from `events`; this is deploy-history-with-commit-detail)
- `/api/analyzer/apps/[appName]/commits/[sha]` — calls analyzer, new
- `/api/analyzer/apps/[appName]/commits/[sha]/logs` — calls analyzer, new
- `/api/analyzer/apps/[appName]/commits/[sha]/diff` — calls analyzer, new
- `/api/analyzer/apps/[appName]/commits/[sha]/files` — calls analyzer, new (accepts `?path=`)
- `/api/analyzer/apps/[appName]/crashes/[crashId]` — calls analyzer, new
- `/api/analyzer/apps/[appName]/stream` — calls analyzer, new (SSE for analysis events)

Net: 9 new proxy routes (down from 10 in the original plan — rollback cut).

### §4.9 Auxiliary decisions — revisions

**`build_duration_ms` refinement:**
- Per-event durations already exist (`deployment_event.duration_ms`). The new column on `deployment` is an aggregate denormalization for the "avg build" summary card query performance. The alternative — aggregating per-event on every request — is fine for MVP but slower; the column is a perf-motivated cache.
- Migration adds the column + backfills from the latest BUILD_FINISHED event per deployment.

**`ENCRYPTION_KEY` requirement:**
- Added to the env var list: the platform uses AES-encrypted env var storage via `EncryptionService`. `ENCRYPTION_KEY` is required; missing it breaks env var read/write.
- `vector-analyzer` does NOT need `ENCRYPTION_KEY` — it never reads app env vars. This stays in `vector-api` only.

### New §4.10 — Existing platform behaviors Time Machine must respect

A new subsection added to capture the three surfaced behaviors:

**Pin/unpin:**
- Rollback auto-pins. Time Machine UI surfaces this in pre-confirm modal and post-rollback banner.
- A "Pinned" badge appears on the app header in Time Machine when `deployment.pinned_image != null`.
- "Unpin" action available from the banner, calls existing `POST /api/apps/{appName}/unpin`.
- Pinned apps receive `UPDATE_AVAILABLE` events on webhooks instead of deploying. These events already appear in `deployment_event` — they just need a distinct visual on the timeline (suggest: small yellow dot with "update available" label).

**ActionLockService:**
- All mutating endpoints (including rollback) use a 5-second in-memory lock per app.
- On 409 Conflict, Time Machine's rollback button shows toast: *"An operation is already in progress for this app. Try again in a moment."*
- This also affects any future automated rollback flows — queue retries with backoff.

**Soft-delete with restore window:**
- Deleted apps stay in the database for 5 minutes with `deleted_at` set before hard-delete.
- Time Machine should filter OUT soft-deleted apps from the app picker and from the timeline of active apps.
- Decision for MVP: if a user navigates to Time Machine for an app during its restore window, show a "This app was deleted. [Restore] [View Archive]" banner. The archive view shows final state only, no new events will appear.
- If budget is tight, simpler MVP: just return 404 for deleted apps in Time Machine, even during the restore window. The restore functionality is already handled in the main dashboard.

### Phase 4 — Rollback section simplified

Since no new rollback endpoint is needed, Phase 4's rollback work is now **frontend-only**:
- Rollback button in the commit-detail header
- Pre-confirm modal explaining pin side-effect
- Calls existing `/api/apps/[appName]/rollback` proxy with `eventId` from the clicked deployment event
- Post-rollback: show pin banner, refresh timeline to show new deployment
- Handle 409 with toast
- Handle 404 (deployment event no longer exists) gracefully
- Disable button if current running commit == target commit

Net effort reduction for Phase 4: ~4 hours (no new backend endpoint to build and test).

### Phase 6 — Virtualized log viewer confirmed as needed

The audit confirms there's no virtualization in `useBuildLogs.ts` (caps at 5000 lines via `RING_BUFFER_CAP`). Time Machine's log playback viewer will need virtualization — this is part of Phase 5's log panel implementation. No plan change beyond confirming this.

### Phase 3 — Data sources corrected

The audit shows several stats already have implementations in `vector-api`:
- Uptime 30d — likely derivable from existing `deployment_event` history (DEPLOY_FINISHED vs CRASHED over 30d window). Confirm during Phase 3 implementation; may not need a new endpoint.
- Deploys 30d — simple COUNT query on `deployment_event` where `event_type = DEPLOY_FINISHED`.
- Last crash — MAX `created_at` from `deployment_event` where `event_type = CRASHED`. Requires Phase 3 of refactor (CRASHED emission) to actually have data.
- Avg build — derived from `deployment_event.duration_ms` BUILD_FINISHED events, or from the new aggregate column.

Phase 3 should add a single `GET /api/analyzer/apps/{name}/stats` endpoint that returns all four. The data already exists or becomes available after refactor Phase 3. No new backend queries needed on `vector-api`.

---

## 3. POST_REFACTOR_ROADMAP.md — changes applied

### Track C (AiProvider/Gemini) — confirmed baseline

Audit confirms:
- Ollama integrated at `com.filipnikolov.launchpad.ai.service` (path will change to `dev.filipnikolov.vector.ai.service` post-rename)
- No abstraction exists; direct calls from `AiController`
- DTOs are internal (`OllamaRequest`, `OllamaResponse`) — new provider-agnostic DTOs needed for the interface
- 8KB log context limit, no timeout, catches `ResourceAccessException`

No track changes. Baseline assumptions were correct.

### Track D (SSE) — scope clarified

Critical clarification: log SSE **exists and works** (`/api/apps/{name}/logs` → `RuntimeLogController:30-57`). The SSE work in Track D is **only for deployment events**, not for logs.

**What exists:**
- Container log SSE endpoint in backend
- `EventSource` consumer in `useBuildLogs.ts` (reference implementation for the pattern)
- `SseEmitter(0L)` with no heartbeat (flagged as a fragility in the audit — Phase 1 adds heartbeat)

**What's missing and needs Track D:**
- Deployment event SSE endpoint (new `/api/events/stream` in `vector-api`)
- Frontend SSE consumer for events (replaces 10s `useEvents` polling)
- Traefik `flushinterval=100ms` label on both log and event SSE paths (audit confirms no such label exists)

Track D now explicitly references `useBuildLogs.ts` as the pattern template and notes the Traefik label as a shared fix.

### VPS deployment checklist — env var addition

The audit surfaced `ENCRYPTION_KEY` as a required env var not previously highlighted in any plan's deployment checklist. Added to the VPS setup / production env var list:
- `VECTOR_ENCRYPTION_KEY` (renamed from `ENCRYPTION_KEY` per track A naming) — required; rotating this invalidates all stored env vars. Back it up securely.

### Track F — `user_account` cleanup

Audit confirms `user_account` table is schema-only, zero code reads or writes it. `D5` decision (drop `api_key_hash`) becomes more ambitious:
- **Option A (conservative):** drop just the `api_key_hash` column, leave the table for future multi-user work
- **Option B (aggressive):** drop the entire `user_account` table and `UserAccount.java` entity since nothing uses them
- Recommendation unchanged: Option A. Keep the table skeleton; drop only the orphaned auth column.

---

## 4. THEME_UPGRADE.md — no changes

Audit found no issues relevant to the theme upgrade. Plan stands as-is.

---

## 5. Summary of decisions reversed by the audit

Decisions in the plans that are now invalid and removed:

- **D6 (TIME_MACHINE_PLAN):** ~~Add `build_duration_ms` column~~ → refined: add as aggregate with backfill from existing per-event duration data
- **D7 (TIME_MACHINE_PLAN):** ~~Add `rollback_from_sha` column~~ → still valid; confirmed missing
- **D8 (TIME_MACHINE_PLAN):** ~~Add `ROLLBACK` to `TriggerSource` enum~~ → already exists, removed from plans
- **Refactor V14:** ~~Add `commit_sha` columns~~ → already exist, removed from migration

---

## 6. New decisions surfaced by the audit (need your answer)

These are things the audit surfaced that need a call:

- [ ] **N1 (TIME_MACHINE):** During a deleted app's 5-minute restore window, should Time Machine show an archive view with a Restore button, OR just 404? Recommendation: 404 for MVP, restore action happens in the main dashboard.
- [ ] **N2 (TIME_MACHINE):** When showing rollback-triggered deploys on the timeline, use a distinct visual (e.g., ↺ badge on the green deploy dot)? Recommendation: yes, subtle but present.
- [ ] **N3 (TIME_MACHINE):** Show `UPDATE_AVAILABLE` events on the timeline for pinned apps? Recommendation: yes, as a small yellow dot — it tells the user "a new commit arrived but you're pinned."
- [ ] **N4 (POST_REFACTOR):** `user_account` cleanup — Option A (drop column only) or Option B (drop entire table)? Recommendation: A.
- [ ] **N5 (REFACTOR Phase 3):** After fixing CRASHED event emission, do we backfill CRASHED events for historical DOWN transitions? Recommendation: no — no reliable source of truth for when past DOWN transitions happened.

---

## 7. What this audit did NOT change

Plan assumptions that were fully correct:
- Rollback needs UX surface in Time Machine (yes, just via existing endpoint)
- NotificationService needs expansion (1 method → 4)
- Postgres NOTIFY/LISTEN needs to be added
- CRASHED event is never emitted (confirmed enum-only)
- No log persistence (confirmed)
- No error boundary on frontend (confirmed)
- No Gemini; Ollama only (confirmed)
- Docker socket on backend (confirmed)
- API key single-shared (confirmed)
- No virtualized log viewer (confirmed)
- No timeline/commit UI exists (confirmed greenfield)

The plans' conceptual framing was accurate; the audit corrected specific assumptions about which pieces already exist.

---

## 8. Risk note

**One mid-risk discovery:** the rollback auto-pin behavior is non-obvious from a UX standpoint. Users who rollback because "the new version broke something" will then push a fix commit and wonder why it doesn't deploy. The Time Machine UI must handle this carefully — either a strong pre-rollback warning, a prominent post-rollback banner, or both. The current plan now specifies both. Recommend testing this flow with a real rollback during Phase 4 to see if the warnings land clearly.

---

## 9. Updated plan documents

The three affected plans have been updated in-place with these changes:
- `REFACTOR_PLAN.md` — Phase 2, 3, 4 sections updated
- `TIME_MACHINE_PLAN.md` — §4.5, §4.2, §4.9, new §4.10, Phase 3, Phase 4, Phase 5 sections updated
- `POST_REFACTOR_ROADMAP.md` — Track D, VPS checklist, Track F updated

Execute against the updated documents, not the originals. This amendment doc is the audit trail; the plans themselves are the execution source of truth.
