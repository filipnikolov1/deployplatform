# Launchpad refactor plan — from audit to execution

**Source:** REVIEW.md (33 findings across 5 sessions)
**Goal:** make the backend resilient and modular, then add bug-analyzer
**Estimated total effort:** ~6–8 focused working days, staged across 7 phases

> **Amendment applied 2026-04-24** — this plan was updated per `CONTEXT_CHECK.md` findings. Key changes: V14 migration no longer adds `commit_sha` (exists) or `ROLLBACK` enum value (exists); adds `build_duration_ms` (aggregate) and `rollback_from_sha`. See `PLAN_AMENDMENTS.md` for the full audit trail.

This document is the bridge between the audit (what's wrong) and execution (what to fix in what order). Execute phases sequentially. Commit between phases so each phase is individually revertable.

---

## 1. Audit triage

### Severity breakdown
- **CRITICAL:** 3 findings (#1, #2, #8) — can hang the entire backend today
- **HIGH:** 13 findings — block resilience goals or the bug-analyzer integration
- **MEDIUM:** 13 findings — make future work harder or cause intermittent issues
- **LOW:** 4 findings — quality-of-life, non-blocking

### Bug-analyzer blockers (must fix before adding the service)
Findings explicitly flagged "Blocks bug-analyzer: YES":
- **#3** — crashes are never recorded as events
- **#4** — recoveries are never recorded as events
- **#15** — no `deployed_at` column on deployments
- **#17** — no FK constraints across tables
- **#18** — no event bus / NOTIFY mechanism
- **#19** — no shared module for event types and DTOs
- **#22** — missing indexes for event-type queries

All seven are addressed in this plan, mostly in Phases 3–5.

### Root-cause clustering
Many findings share a single root cause. Grouping these makes for cleaner PRs:

| Cluster | Findings | Root cause |
|---|---|---|
| Missing HTTP timeouts | #8, #9, #11, #13, #27, part of #1 | No client-level timeout on any outbound HTTP |
| Blocking I/O inside `@Transactional` | #1, #6 | Docker ops run inside DB transactions |
| Sync webhook handling | #2, #5 | Webhook thread blocks for entire deploy + layering violation |
| Missing state-change events | #3, #4, #26 | Uptime monitor doesn't emit events; interface is incomplete |
| Schema gaps | #15, #17, #16, #21, #22, #14, #20 | Schema grew by accretion, not design |
| Missing modularity primitives | #18, #19 | No events fan-out, no shared types |
| Frontend resilience | #27, #28, #29, #30, #32, #33 | Missing timeouts + defensive defaults |
| SSE edge cases | #7, #23 | No heartbeat, no reconnect throttle |

---

## 2. Phased execution plan

### Phase 1 — Timeouts everywhere (safety net)

**Goal:** prevent any single external dependency from hanging the backend indefinitely.

**Findings:** #8, #9, #11, #13, #27, part of #1 (the `latch.await` timeout only)

**Why first:** pure defensive additions, zero logic changes, lowest risk of regression, biggest immediate resilience win. After this phase the backend cannot hang indefinitely on any outbound call.

**Changes:**
- `DockerClientConfig` — add `connectionTimeout(5s)`, `responseTimeout(30s)`
- `DockerServiceImpl` — change `latch.await()` → `latch.await(10, TimeUnit.MINUTES)`
- `NotificationServiceImpl` — add `SimpleClientHttpRequestFactory` with read timeout `10s`
- `UpdaterClient` — add factory with read timeout `5s`
- `GitHubServiceImpl` — add factory with connect `3s`, read `5s`
- `frontend/src/app/api/apps/[appName]/logs/runtime/route.ts` — add `AbortSignal.timeout(10_000)` composed with `req.signal`

**Effort:** ~2 hours. Single PR. No decisions required.

**Verification:** temporarily block outbound network to one target (iptables rule, or `/etc/hosts` override) and confirm the backend times out cleanly instead of hanging.

---

### Phase 2 — Transaction & async surgery (the hard one)

**Goal:** stop holding DB connections open while doing Docker I/O. Make webhook processing async.

**Findings:** #1 (full fix), #6, #2, #5

**Why second:** Phase 1's timeouts bound how long things can hang, but this phase fixes the actual architectural mistake — Docker ops don't belong inside `@Transactional`. This is the single most important correctness refactor in the audit.

**Changes:**

1. **Split `DeploymentServiceImpl.createDeployment` into three parts:**
   - `@Transactional` **pre-phase:** validate, save `PENDING` row, emit `DEPLOY_STARTED`, **commit**
   - **Non-transactional middle phase:** `dockerService.pullAndRun(...)` — no DB connection held
   - `@Transactional` **post-phase:** update status to `RUNNING`/`FAILED`, emit `DEPLOY_FINISHED`/`FAILED`, **commit**

2. **Apply the same three-phase split to `restartDeployment`** and to the restart triggered inside `updateSubdomain`.

3. **Make the webhook async:**
   - `DeployHookController.handleDeploy` — validate the webhook, acquire `ActionLockService` lock, immediately return `202 Accepted` with the deployment ID
   - Dispatch the actual deploy work via `@Async` or a dedicated `ExecutorService`
   - Return `409 Conflict` if the lock is held (prevents GitHub-retry duplicates)
   - **Preserve `ActionLockService` semantics:** the async task must release the lock when Docker work completes or fails. The existing 5-second lock applies to all mutating endpoints (restart, stop, rollback, subdomain, deploy) — this refactor must maintain that guarantee. Release in a `finally` block or equivalent.

4. **Fix layering in `DeployHookController`** (#5):
   - Remove `DeploymentRepository` from the constructor
   - Move the pinned-image check into a new `DeploymentService.handleWebhookDeploy(req)` method that runs entirely inside a single `@Transactional`

**Effort:** 1–2 days. Single focused PR. Highest-risk PR in the plan — requires careful testing.

**Testing:**
- Deploy an app with a slow image pull (use a large image, simulate network slowness)
- Verify DB connection count stays low during the pull
- Trigger two webhooks in quick succession for the same app, confirm the second returns `409`
- Kill the backend mid-deploy, restart, verify Phase 3's reconciler handles the stuck `PENDING` (see Phase 3 #10)

**Decisions needed:**
- **D1:** Async execution — Spring `@Async` with a dedicated `TaskExecutor` bean (simple) or a manually-managed `ExecutorService` (more control)? My recommendation: `@Async` with a named executor bean, size 4 threads.

---

### Phase 3 — Events, notifications, and recovery

**Goal:** fill the event emission gaps and complete the notification interface. After this phase, crashes and recoveries are visible in the activity feed, users get emails for all meaningful state changes, and stuck deploys are recovered on backend startup.

**Findings:** #3, #4, #26, #12, #10

**Why third:** Phase 2 added `DEPLOY_STARTED` / `DEPLOY_FINISHED` properly; now complete the picture with `CRASHED`, `RESTARTED`, and deploy-failed notifications. Also adds the startup reconciler for stuck `PENDING` rows — the failure mode from crashing between Phase 2's pre- and post-phases.

**Changes:**

1. **Extend `NotificationService` interface** (#26) to include:
   ```java
   void sendDownAlert(String appName);        // already exists
   void sendRecoveredAlert(String appName);
   void sendDeployFailedAlert(String appName, String errorMessage);
   void sendCrashedAlert(String appName);
   ```
   Implement each in `NotificationServiceImpl` using the existing Resend pattern.

2. **Emit CRASHED event** in `UptimeMonitorServiceImpl` when RUNNING→DOWN (#3). Inject `DeploymentEventService`.

3. **Emit RESTARTED event + send recovery email** when DOWN→RUNNING (#4).

4. **Add per-app alert rate limiting** (#12) using a `ConcurrentHashMap<String, Long>` with a 15-minute cooldown. Reset on recovery.

5. **Wire `sendDeployFailedAlert`** into the FAILED branch of `createDeployment`'s post-phase (added in Phase 2).

6. **Add startup reconciler for stuck PENDING** (#10) — `@PostConstruct` bean or add to `SelfAppBootstrap`:
   ```java
   findByStatusAndUpdatedAtBeforeAndDeletedAtIsNull(PENDING, now().minusMinutes(5))
     .forEach(d -> { mark FAILED; emit event; })
   ```
   Add the corresponding repository method.

7. **All notification sends should be `@Async`** so slow email delivery never blocks the scheduler or request threads. Add a `@EnableAsync` if not already present, and a dedicated executor for notifications.

**Effort:** 1 day. Single PR.

**Decisions needed:**
- **D2:** Alert rate limit window — 15 minutes as the audit proposes, or something different? 15 min means one email per app per crash-loop, which seems right for a single-user platform.
- **D3:** Should `sendDeployFailedAlert` include the full error message? Short answer: yes, but truncated to ~500 chars for email size.

---

### Phase 4 — Schema consolidation (one migration, many fixes)

**Goal:** bring the schema up to date. One Flyway migration, one companion PR for code changes.

**Findings:** #21, #15, #17, #16, #22, #14, #20

**Why fourth:** many findings share a single migration. Doing them together is cheaper than seven separate migrations and gives a clean V14 checkpoint.

**Changes in a single `V14__schema_consolidation.sql` migration:**

> **Context check note:** several columns/enum values the original plan proposed to add already exist. The list below reflects only what's actually missing per `CONTEXT_CHECK.md`. Do not re-add `commit_sha` (both tables have it), and do not re-add `ROLLBACK` to `TriggerSource` (already present).

```sql
-- #21: commit_message was truncating long commits
ALTER TABLE deployment ALTER COLUMN commit_message TYPE TEXT;

-- #15: authoritative "current container started at" timestamp
ALTER TABLE deployment ADD COLUMN last_deployed_at TIMESTAMP;

-- NEW: aggregate build duration for the avg-build summary card query.
-- deployment_event.duration_ms already exists per-event; this is a denormalized
-- cache of the latest BUILD_FINISHED duration for fast dashboard queries.
ALTER TABLE deployment ADD COLUMN build_duration_ms BIGINT;
-- Backfill from existing event history:
UPDATE deployment d SET build_duration_ms = (
  SELECT duration_ms FROM deployment_event
  WHERE app_name = d.app_name AND event_type = 'BUILD_FINISHED'
  ORDER BY created_at DESC LIMIT 1
);

-- NEW: capture prior commit on rollback events for Time Machine timeline.
ALTER TABLE deployment_event ADD COLUMN rollback_from_sha VARCHAR(40);

-- #17: FK constraints across the schema
ALTER TABLE env_var
    ADD CONSTRAINT fk_envvar_app FOREIGN KEY (app_name)
    REFERENCES deployment (app_name) ON DELETE CASCADE;

ALTER TABLE pending_self_update
    ADD CONSTRAINT fk_pending_app FOREIGN KEY (app_name)
    REFERENCES deployment (app_name) ON DELETE CASCADE;

ALTER TABLE deployment_event
    ADD CONSTRAINT fk_event_app FOREIGN KEY (app_name)
    REFERENCES deployment (app_name) ON DELETE SET NULL;
    -- SET NULL preserves event history after app deletion (see D4 below)

-- #22: missing indexes
CREATE INDEX idx_deployment_status_live
    ON deployment (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_event_app_type_created
    ON deployment_event (app_name, event_type, created_at DESC);

-- #20: drop dead column OR leave in place (depends on D5 below)
-- ALTER TABLE user_account DROP COLUMN api_key_hash;
```

**Companion code changes:**
- `Deployment.java` — remove `@Column(length = 500)` from `commitMessage`; add `lastDeployedAt` and `buildDurationMs` fields
- `DeploymentServiceImpl` — set `lastDeployedAt` in the post-phase of createDeployment and restartDeployment (#15); update `buildDurationMs` when handling BUILD_FINISHED event recording
- `DeploymentEvent.java` — add `rollbackFromSha` field
- `DeploymentServiceImpl.rollback` — populate `rollback_from_sha` with the currently-running commit SHA before performing the rollback (for audit trail + Time Machine timeline display)
- `DeploymentServiceImpl.hardDeleteExpired` — replace `findAll()` with `findByDeletedAtIsNotNullAndDeletedAtBefore(cutoff)` (#14); also delete env_var rows explicitly if FK cascade is not relied upon (#16)
- `DeploymentRepository` — add the targeted query methods (#14, #10)
- `EnvVarRepository` — add `deleteByAppName(String)` for explicit cleanup fallback (#16)

**Effort:** 1 day. Can run in parallel with Phase 3 if you have time.

**Decisions needed:**
- **D4:** `deployment_event.app_name` ON DELETE behavior:
  - `CASCADE` — events deleted with app (loses audit history)
  - `SET NULL` — events preserved, app_name becomes null after deletion (my recommendation)
  - `NO ACTION` — cannot delete app while events exist (blocks hard delete)
- **D5:** `user_account.api_key_hash` — drop it (simpler, single-user model) or keep and wire up per-user auth later? For now I'd drop it and revisit if the product ever grows past you.

---

### Phase 5 — Bug-analyzer prerequisites (the unlock)

**Goal:** everything needed before bug-analyzer can be built cleanly.

**Findings:** #19, #18

**Why fifth:** Phases 1–4 fix what's broken. Phase 5 adds what's missing. After this, bug-analyzer can start.

**Changes:**

1. **Create a shared Maven module** (#19): `launchpad-events`
   - Top-level Maven module at repo root
   - Contains only: `DeploymentEventType`, `DeploymentEventStatus`, `DeploymentStatus`, `TriggerSource` enums, `DeploymentEventDto`, a new `PlatformEvent` record
   - No Spring annotations, no JPA — pure Java. Depend on `jackson-annotations` for JSON.
   - Both `launchpad-backend` and future `bug-analyzer` declare it as a dependency
   - Requires converting the current backend into a multi-module Maven project

2. **Add Postgres NOTIFY trigger** on `deployment_event` inserts (#18) in a new `V15__event_notify.sql` migration:
   ```sql
   CREATE OR REPLACE FUNCTION notify_deployment_event() RETURNS trigger AS $$
   BEGIN
     PERFORM pg_notify('launchpad_events',
       json_build_object('id', NEW.id, 'app_name', NEW.app_name,
                         'event_type', NEW.event_type, 'status', NEW.status)::text);
     RETURN NEW;
   END;
   $$ LANGUAGE plpgsql;

   CREATE TRIGGER trg_deployment_event_notify
   AFTER INSERT ON deployment_event
   FOR EACH ROW EXECUTE FUNCTION notify_deployment_event();
   ```

**Effort:** 1–2 days. The Maven module refactor is the slow part — every import statement in the current backend stays the same, but the module boundary has to be introduced carefully.

**Decisions needed:**
- **D6:** Shared module format — Java Maven module (simplest if bug-analyzer is also Java/Kotlin, which matches everything else you've built) or OpenAPI spec with generated clients (more work, language-agnostic). My recommendation: Maven module. Bug-analyzer will be Spring Boot, same ecosystem, zero friction.

---

### Phase 6 — Frontend resilience polish

**Goal:** the frontend stops silently failing or leaking requests.

**Findings:** #7, #23, #28, #29, #30, #32, #33

**Why sixth:** none of these block bug-analyzer, but they're cheap and collectively make the UI feel reliable instead of flaky. Do in a single PR when you have a couple of free hours.

**Changes:**
- `RuntimeLogController` — add 15-second SSE heartbeat via `emitter.send(SseEmitter.event().comment("keepalive"))` on a scheduled `@Async` loop (#7)
- `RuntimeLogController` — detect stopped container upfront and send `retry: 60000` SSE directive to throttle browser reconnects (#23)
- Frontend `useBuildLogs` — handle the `event: done` terminal message by closing the `EventSource` and not reconnecting (#23)
- Frontend root page — wrap `<AppGrid />` in `<ErrorBoundary>` (#28)
- `useContainerStats` — return `error` field (#29)
- `useApps` — return `apps: data ?? []` instead of `apps: data` (#30)
- All SWR fetchers — add `AbortSignal.timeout(15_000)` to the fetch (#32)
- `useToast` — raise cap from 3 to 6, or exempt persistent progress toasts from eviction (#33)

**Effort:** 4–6 hours. Single PR.

**Decisions needed:**
- **D7:** SSE heartbeat interval — 15s is conservative and safe; 30s is lighter on traffic. Your call; 15s default.

---

### Phase 7 — Deferred / handled elsewhere

These findings are **intentionally not** in Phases 1–6. They get addressed as part of other planned work.

| Finding | Where it gets handled |
|---|---|
| **#24** — AI call blocks thread, no streaming | Handled in the Gemini migration. `AiProvider` abstraction + Gemini's native streaming replace the current Ollama blocking call. Don't fix it twice. |
| **#25** — 10-second polling for events; lag vs. SSE logs | Handled as part of the SSE event stream endpoint, which is most naturally built alongside bug-analyzer since bug-analyzer also wants to push events to the UI. |
| **#31** — Preferences toggles with no backend wiring | Pair with Phase 3's #26 if you want full coverage. Otherwise remove the orphaned toggles from the UI until backend catches up. Not blocking anything. |

---

## 3. Decisions checklist

Before starting execution, make these decisions. I've noted my recommendation next to each — you can go with them or override. The important thing is to decide before starting, not mid-PR.

- [ ] **D1 (Phase 2):** Async webhook execution style → `@Async` with named `TaskExecutor` bean, 4 threads
- [ ] **D2 (Phase 3):** Alert rate-limit window → 15 minutes per app
- [ ] **D3 (Phase 3):** Include error message in deploy-failed email → yes, truncate to 500 chars
- [ ] **D4 (Phase 4):** `deployment_event` FK ON DELETE → `SET NULL` (preserves audit history)
- [ ] **D5 (Phase 4):** `user_account.api_key_hash` → drop it, single-key model
- [ ] **D6 (Phase 5):** Shared module format → Maven module (Java)
- [ ] **D7 (Phase 6):** SSE heartbeat interval → 15 seconds

---

## 4. Timeline

Rough calendar placement, assuming ~3 evenings per phase for the big ones:

```
Week 1:  Phase 1 (timeouts)         — evening 1
         Phase 2 (transactions)     — evenings 2-4
Week 2:  Phase 3 (events/notif)     — evenings 1-2
         Phase 4 (schema)           — evenings 3-4
Week 3:  Phase 5 (shared module)    — evenings 1-3
         Phase 6 (frontend polish)  — evening 4
Week 4+: Bug-analyzer development begins
```

This is a guideline, not a deadline. Do each phase when you have the energy for it. Do NOT interleave refactor work with other feature development — the transactional surgery in Phase 2 especially needs focused attention.

Total effort estimate: **~6–8 focused working days** spread over 2–3 calendar weeks.

---

## 5. Execution workflow per phase

For each phase:

1. **Branch:** `refactor/phase-N-short-description` off `dev`
2. **Write a Claude Code prompt** — ask this planning chat to produce one when you're ready for that phase. Each phase's prompt will include the specific findings to address, the decisions you've made, and constraints for scope
3. **Execute in Claude Code** — preferably Sonnet, same reasoning as the audit
4. **Review the diff manually** — the audit was the easy part; verifying the fix is the real work
5. **Run the phase's verification steps** (listed in each phase above)
6. **Merge to `dev`**, tag with phase name, move on

Do NOT try to do all seven phases in one sitting. The transactional refactor in Phase 2 alone is a full day's careful work.

---

## 6. What this plan does NOT cover

Explicitly out of scope for this refactor:

- **Bug-analyzer itself.** Phases 1–6 are prep. The actual bug-analyzer service is the next project after this refactor lands.
- **The theme upgrade.** Separate track, documented in `THEME_UPGRADE.md`. Execute independently, not interleaved.
- **The Vector rename.** Decide separately whether to rename during this refactor or after. I'd rename first (one day of find-and-replace on a clean tree is easier than on a refactored one).
- **Gemini migration.** The `AiProvider` abstraction is a separate PR, should happen after Phase 5 finishes so it can pick up the new shared module.
- **iOS app.** Future course work, doesn't depend on any of this.

---

## 7. Risk notes

Honest about what could go wrong:

- **Phase 2 is the scariest change** in this plan. Splitting `@Transactional` boundaries can introduce subtle consistency bugs. Test with the backend killed mid-deploy multiple times. The reconciler in Phase 3 is insurance against the worst case.
- **Phase 5's Maven multi-module refactor** is mechanically tricky. Plan for half a day just fighting Maven config, even if the logic changes are small. Start with the smallest possible shared module (just the enums, nothing else) and grow it.
- **The FK constraints in Phase 4** will fail to apply if you have any orphaned rows in `env_var` or `pending_self_update` today. The migration needs a preceding cleanup query, or it'll abort. Run a `SELECT * FROM env_var WHERE app_name NOT IN (SELECT app_name FROM deployment)` before the migration and either delete or reassign the orphans.
- **Every phase's "verification" section should actually be run.** Mechanical refactors pass the build but fail at runtime in creative ways.

---

## 8. When you come back to execute

Start here:

1. Re-read §3 and mark the decisions.
2. Pick a phase. Usually Phase 1, but if you've got energy for the big one, you could start with Phase 2 after getting Phase 1 merged.
3. Come back to this planning chat and say *"I'm ready to execute Phase N, decisions are: ..."* and I'll produce a Claude Code prompt for that phase.
4. Execute, verify, commit.
5. Repeat.
