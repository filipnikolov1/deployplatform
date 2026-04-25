# Launchpad — Architectural Audit

---

## Session 1 — Flow correctness

Flows traced:
1. GitHub webhook → deployment created → container started → running
2. Container crashes → detected → recorded
3. Manual redeploy triggered → old container stopped → new started

Files read (11 of 15 limit):
- `DeployHookController.java`
- `DeploymentServiceImpl.java`
- `DockerServiceImpl.java`
- `DeploymentController.java`
- `ContainerStatsServiceImpl.java`
- `Deployment.java`
- `UptimeMonitorServiceImpl.java`
- `DeploymentEvent.java`
- `DeploymentEventServiceImpl.java`
- `DeploymentEventType.java`
- `RuntimeLogController.java`

---

### Finding #1 — Blocking Docker pull inside @Transactional holds DB connection for duration of image pull

**Severity:** CRITICAL
**Category:** BUG
**Blocks bug-analyzer:** NO

**What's wrong:**
`DeploymentServiceImpl.createDeployment` (line 48) and `restartDeployment` (line 129) are both `@Transactional`. Both call `dockerService.pullAndRun(...)`, which internally does `latch.await()` with no timeout (`DockerServiceImpl:81`). A Postgres connection is held open for the entire image pull, which can take minutes on large images or slow network. With a default HikariCP pool of 10, 10 concurrent deploys deadlock the entire application — all threads block waiting for a DB connection that won't be released.

**Evidence:**
```java
// DeploymentServiceImpl:47-93
@Override
@Transactional
public Deployment createDeployment(CreateDeploymentRequest req) {
    ...
    // DB connection held from here...
    dockerService.pullAndRun(req.imageName(), ...); // blocks for N minutes
    // ...to here
    return deploymentRepository.save(deployment);
}

// DockerServiceImpl:58-81
CountDownLatch latch = new CountDownLatch(1);
pullCmd.exec(new ResultCallback<PullResponseItem>() { ... });
latch.await(); // NO TIMEOUT — blocks indefinitely
```

**Impact:**
Under any realistic load (or a slow DockerHub response), the connection pool exhausts and all API requests — including health probes — start timing out.

**Proposed fix:**
1. Split `createDeployment` into two phases: a short `@Transactional` write (save deployment record, emit DEPLOY_STARTED event, commit), then a non-transactional Docker call, then a second short `@Transactional` write (update status, emit DEPLOY_FINISHED/FAILED event).
2. Add a timeout to `latch.await()`: `latch.await(10, TimeUnit.MINUTES)` and throw a `RuntimeException` on timeout.
3. Same split applies to `restartDeployment`.

**Effort:** MEDIUM

---

### Finding #2 — Webhook HTTP thread blocks for entire deploy; GitHub retries cause duplicate deploys

**Severity:** CRITICAL
**Category:** BUG
**Blocks bug-analyzer:** NO

**What's wrong:**
`DeployHookController.handleDeploy` calls `deploymentService.createDeployment(req)` synchronously (line 140) and only returns `ResponseEntity.ok()` after the full deploy completes. GitHub's webhook delivery times out after 10 seconds with no response. For any non-trivial image pull, GitHub marks the delivery as failed and will retry the webhook after ~1 minute. The retry arrives while the original deploy is still in progress, and because `DeployHookController` does NOT acquire the `ActionLockService` lock, two concurrent `createDeployment` calls for the same app race: the second call's `pullAndRun` will call `stopAndRemoveContainer(appName)` (DockerServiceImpl:88) and kill the container the first call just started.

**Evidence:**
```java
// DeployHookController:140-141 — no async, no lock, returns only after full deploy
deploymentService.createDeployment(req);
return ResponseEntity.ok().build();

// DeploymentController:50 — lock IS used for manual restart, but not here
var maybeHandle = lockService.tryLock("app:" + appName);
```

**Impact:**
Every webhook for an image that takes >10s to pull will produce at least one duplicate deploy attempt, with the second deploy potentially orphaning the first container mid-start.

**Proposed fix:**
1. Make the deploy asynchronous: on webhook receipt, validate, immediately save a PENDING deployment record and return `202 Accepted`. Execute the Docker work in a `@Async` thread or a dedicated executor.
2. Add an `ActionLockService.tryLock("app:" + appName)` guard in the webhook handler before dispatching, same pattern as `DeploymentController:50-58`, and return `409` on lock contention instead of queuing a duplicate.

**Effort:** MEDIUM

---

### Finding #3 — Container crash never recorded as a DeploymentEvent

**Severity:** HIGH
**Category:** BUG
**Blocks bug-analyzer:** YES

**What's wrong:**
`UptimeMonitorServiceImpl.checkAll` (lines 40-47) detects that a RUNNING container has stopped, sets status to `DOWN`, and calls `notificationService.sendDownAlert`. It does **not** call `eventService.record(...)` to write a `CRASHED` `DeploymentEvent`. The `CRASHED` value exists in `DeploymentEventType` (line 10) but is never emitted by any code path. As a result, crash events have no audit trail and cannot be queried. A bug-analyzer that subscribes to `deployment_event` rows will never see container crashes.

**Evidence:**
```java
// UptimeMonitorServiceImpl:40-47
if (!isContainerRunning(app.getAppName())) {
    app.setStatus(DeploymentStatus.DOWN);
    app.setUpdatedAt(LocalDateTime.now());
    deploymentRepository.save(app);
    notificationService.sendDownAlert(app.getAppName()); // ← no eventService.record(CRASHED, ...)
}
```

**Impact:**
Crash events are invisible to the activity log, to any future event subscriber (bug-analyzer), and to the existing audit trail.

**Proposed fix:**
Inject `DeploymentEventService` into `UptimeMonitorServiceImpl`. After `deploymentRepository.save(app)`, add:
```java
eventService.record(DeploymentEventType.CRASHED, DeploymentEventStatus.FAILURE,
    app.getAppName(), null, null, "Container stopped unexpectedly");
```

**Effort:** TRIVIAL

---

### Finding #4 — App recovery is silent: no notification and no event recorded

**Severity:** HIGH
**Category:** BUG
**Blocks bug-analyzer:** YES

**What's wrong:**
`UptimeMonitorServiceImpl.checkAll` (lines 51-58) detects that a DOWN container has started running again. It sets status to `RUNNING` and saves — but neither sends a recovery notification nor records a `DeploymentEvent`. The planned email trigger "uptime recovered" is entirely absent. The recovery state change is invisible to any event consumer.

**Evidence:**
```java
// UptimeMonitorServiceImpl:51-58
if (isContainerRunning(app.getAppName())) {
    app.setStatus(DeploymentStatus.RUNNING);
    app.setUpdatedAt(LocalDateTime.now());
    deploymentRepository.save(app); // ← no notification, no event
}
```

**Impact:**
Users are never told when a crashed app comes back up; a bug-analyzer cannot correlate crash-to-recovery windows.

**Proposed fix:**
1. Add a `sendRecoveredAlert(appName)` method to `NotificationService` and call it here.
2. Record a recovery event:
   ```java
   eventService.record(DeploymentEventType.RESTARTED, DeploymentEventStatus.SUCCESS,
       app.getAppName(), null, null, "Container recovered");
   ```

**Effort:** SMALL

---

### Finding #5 — DeployHookController directly injects DeploymentRepository; pinned-image check is non-transactional and races

**Severity:** HIGH
**Category:** FLOW
**Blocks bug-analyzer:** NO

**What's wrong:**
`DeployHookController` injects `DeploymentRepository` directly (line 8, 41) and performs a read-then-write sequence (lines 119–130) outside any `@Transactional` boundary. The `handleDeploy` method is not annotated `@Transactional`, so the `findByAppName` read and the subsequent `deploymentRepository.save(d)` execute in separate auto-commit transactions. A concurrent webhook can read the same deployment row between those two operations and both proceed to call `createDeployment` with inconsistent state. Additionally, bypassing the service layer means business rules in `DeploymentService` are skipped for this code path.

**Evidence:**
```java
// DeployHookController:119-130 — no @Transactional, no lock
Optional<Deployment> existing = deploymentRepository.findByAppName(appName);
if (existing.isPresent() && existing.get().getPinnedImage() != null) {
    Deployment d = existing.get();
    ...
    deploymentRepository.save(d); // separate auto-commit transaction
}
deploymentService.createDeployment(req); // only if not pinned
```

**Impact:**
Concurrent webhooks for a pinned app can both pass the `getPinnedImage() != null` check and both call `createDeployment`, resulting in two simultaneous deploys for the same app.

**Proposed fix:**
1. Move the pinned-image check into `DeploymentService.createDeployment` (or a new `DeploymentService.handleWebhook` method) so it runs inside the existing `@Transactional`.
2. Remove `DeploymentRepository` from `DeployHookController`'s constructor — the controller should only depend on services.

**Effort:** SMALL

---

### Finding #6 — `updateSubdomain` nests a full Docker restart inside a long-running @Transactional

**Severity:** MEDIUM
**Category:** BUG
**Blocks bug-analyzer:** NO

**What's wrong:**
`DeploymentServiceImpl.updateSubdomain` (line 251) is `@Transactional` and calls `restartDeployment(appName)` (line 277) when the app is RUNNING. `restartDeployment` is also `@Transactional(REQUIRED)` — Spring merges them into one transaction. The combined transaction spans: subdomain conflict checks → DB save → Docker image pull (blocking, potentially minutes) → container stop → container start → DB save. This is the same DB connection exhaustion risk as Finding #1, triggered by a subdomain change on a running app.

**Evidence:**
```java
// DeploymentServiceImpl:251,276-278
@Transactional
public Deployment updateSubdomain(String appName, String subdomain) {
    ...
    deploymentRepository.save(deployment);
    if (deployment.getStatus() == DeploymentStatus.RUNNING) {
        restartDeployment(appName); // ← merges into this transaction, adds Docker pull time
    }
    ...
}
```

**Impact:**
A subdomain change on a RUNNING app holds a DB connection for the full duration of the image pull — same exhaustion risk as Finding #1, compounded.

**Proposed fix:**
Apply the same two-phase split from Finding #1's fix. Specifically: commit the subdomain update first (end the outer `@Transactional`), then trigger the restart as a separate non-transactional async operation. Alternatively, restructure `updateSubdomain` to save and commit, then call a `@Async` restart.

**Effort:** SMALL

---

### Finding #7 — SSE log stream: emitter timeout is 0 (infinite); Docker stream orphaned on idle client disconnect

**Severity:** MEDIUM
**Category:** RESILIENCE
**Blocks bug-analyzer:** NO

**What's wrong:**
`RuntimeLogController.streamLogs` creates `new SseEmitter(0L)` (line 32) — a no-timeout emitter, which is correct for streaming. However, if the client disconnects while the container is idle (not producing log lines), the emitter's `onError` callback only fires on the next write attempt. With no heartbeat and a no-timeout emitter, the Docker `ResultCallback` stream stays open indefinitely, holding a Docker daemon connection per idle client. The cleanup callbacks (`onCompletion`, `onTimeout`, `onError`) are wired correctly; the gap is that none of them fire until the next write.

**Evidence:**
```java
// RuntimeLogController:32-44
SseEmitter emitter = new SseEmitter(0L); // no timeout → onTimeout never fires
Closeable stream = dockerService.streamContainerLogs(appName, TAIL_LINES,
    line -> { emitter.send(...); }, // onError fires here only if client disconnected
    emitter::completeWithError,
    emitter::complete);
```

**Impact:**
On a quiet container with many client reconnects (e.g., browser tab repeatedly opened and closed), Docker stream handles accumulate until the backend is restarted.

**Proposed fix:**
1. Add a periodic heartbeat comment on the SSE stream (e.g., every 15s): `emitter.send(SseEmitter.event().comment("keepalive"))`. This triggers a write attempt that will fail (and thus fire `onError` → `closeStream`) immediately when the client has disconnected.
2. Alternatively, set a reasonable timeout `new SseEmitter(5 * 60 * 1000L)` and let the frontend reconnect.

**Effort:** TRIVIAL

---

## Session 2 — Resilience

Files read (11 of 15 limit):
- `NotificationServiceImpl.java`
- `ActionLockService.java`
- `DockerClientConfig.java`
- `GitHubServiceImpl.java`
- `GlobalExceptionHandler.java`
- `SelfAppUpdateExecutor.java`
- `UpdaterClient.java`
- `SelfAppBootstrap.java`
- `PendingSelfUpdate.java`
- `OllamaServiceImpl.java`
- `OllamaRestClientConfig.java`

---

### Finding #8 — Docker HTTP client has no timeout; Docker daemon hang makes the entire backend unresponsive

**Severity:** CRITICAL
**Category:** RESILIENCE
**Blocks bug-analyzer:** NO

**What's wrong:**
`DockerClientConfig` builds a `ZerodepDockerHttpClient` with no `connectionTimeout` or `responseTimeout` set (lines 23–25). Every synchronous Docker API call — `createContainerCmd.exec()`, `startContainerCmd.exec()`, `stopContainerCmd.exec()`, `inspectContainerCmd.exec()`, `statsCmd.exec()` — runs over this client and relies on the OS TCP stack's timeout (commonly 2–15 minutes). Combined with Finding #1 (these calls run inside `@Transactional`), a single Docker daemon slowdown holds DB connections open for the OS timeout duration. Five concurrent operations exhaust a 10-connection HikariCP pool; all subsequent requests (including health probes) queue and eventually time out.

**Evidence:**
```java
// DockerClientConfig:23-25
ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
        .dockerHost(URI.create(dockerSocket))
        .build();  // no connectTimeout, no responseTimeout
```

**Impact:**
A temporarily unresponsive Docker daemon causes the backend to become fully unresponsive within seconds, recovering only after OS TCP timeouts expire.

**Proposed fix:**
```java
ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
        .dockerHost(URI.create(dockerSocket))
        .connectionTimeout(Duration.ofSeconds(5))
        .responseTimeout(Duration.ofSeconds(30))
        .build();
```
Note: the pull latch timeout (Finding #1) is separate and needs its own `latch.await(10, TimeUnit.MINUTES)` fix.

**Effort:** TRIVIAL

---

### Finding #9 — Resend RestClient has no timeout; Resend hang blocks the uptime-monitor scheduler thread

**Severity:** HIGH
**Category:** RESILIENCE
**Blocks bug-analyzer:** NO

**What's wrong:**
`NotificationServiceImpl` builds its `RestClient` with no timeout (lines 34–38). `sendDownAlert` is called synchronously inside `UptimeMonitorServiceImpl.checkAll()`, a `@Scheduled(fixedRate = 60000)` method running on Spring's single-thread scheduler pool. If Resend's API is slow or unreachable, `checkAll()` blocks on the HTTP call for the OS TCP timeout. While blocked, no further uptime checks run — any app that crashes during this window goes undetected. If multiple apps are down simultaneously, sequential notification calls compound the stall.

**Evidence:**
```java
// NotificationServiceImpl:34-38 — no read/connect timeout
this.restClient = RestClient.builder()
        .baseUrl("https://api.resend.com")
        .defaultHeader("Authorization", "Bearer " + apiKey)
        .build();

// UptimeMonitorServiceImpl:46 — synchronous on the scheduler thread
notificationService.sendDownAlert(app.getAppName());
```

**Impact:**
A Resend outage silences uptime monitoring for its duration; apps that crash while Resend is hung go undetected and unemailed.

**Proposed fix:**
1. Add a read timeout via `SimpleClientHttpRequestFactory.setReadTimeout(Duration.ofSeconds(10))` when building the Resend `RestClient` — same pattern as `OllamaRestClientConfig`.
2. Optionally dispatch `sendDownAlert` via `@Async` so a slow Resend call never blocks the scheduler thread.

**Effort:** SMALL

---

### Finding #10 — Deployment stuck in PENDING forever after backend crash mid-deploy

**Severity:** HIGH
**Category:** RESILIENCE
**Blocks bug-analyzer:** NO

**What's wrong:**
`DeploymentServiceImpl.createDeployment` (line 70) saves `status=PENDING` and commits (the save at line 72 flushes within the `@Transactional`), then performs Docker operations that take minutes. If the JVM is killed between that commit and the final `deploymentRepository.save(deployment)` at line 96, the row stays `PENDING` indefinitely. Same applies to `restartDeployment` (line 133). Neither `SelfAppBootstrap` nor any scheduled task scans for stale PENDING rows on startup; `UptimeMonitorServiceImpl` only queries RUNNING and DOWN.

**Evidence:**
```java
// DeploymentServiceImpl:70-96
deployment.setStatus(DeploymentStatus.PENDING);
deploymentRepository.save(deployment);        // ← committed; JVM kill here = stuck PENDING forever

// Docker pull + create + start happens here (minutes) ...

deployment.setStatus(DeploymentStatus.RUNNING); // never reached on crash
return deploymentRepository.save(deployment);
```

**Impact:**
After any unexpected backend restart during an active deploy, affected apps show as PENDING forever with no way for the UI or scheduler to recover them automatically.

**Proposed fix:**
In `SelfAppBootstrap.bootstrap()` (or a dedicated `DeploymentReconciler` @PostConstruct bean), add:
```java
LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(5);
deploymentRepository.findByStatusAndUpdatedAtBeforeAndDeletedAtIsNull(
        DeploymentStatus.PENDING, staleThreshold)
    .forEach(d -> {
        d.setStatus(DeploymentStatus.FAILED);
        d.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(d);
        eventService.record(FAILED, FAILURE, d.getAppName(), null, null,
                "interrupted by backend restart");
    });
```
Add the required `findByStatusAndUpdatedAtBefore...` method to `DeploymentRepository`.

**Effort:** SMALL

---

### Finding #11 — UpdaterClient poll loop has no per-call timeout; the 15-minute deadline is unreliable when the updater hangs

**Severity:** HIGH
**Category:** RESILIENCE
**Blocks bug-analyzer:** NO

**What's wrong:**
`UpdaterClient` builds its `RestClient` with no HTTP timeout (line 20). The initial `updaterClient.update()` call in `SelfAppUpdateExecutor.executeUpdate()` (line 60) runs before the 15-minute deadline is set — if the updater hangs on that call, the @Async thread is stuck indefinitely with no deadline at all. Inside the poll loop, each `updaterClient.status()` call (line 80) can block for the OS TCP timeout (~2 minutes) before returning `null`, so the `System.currentTimeMillis() < deadline` guard only fires after the call returns. In the worst case the thread runs for `MAX_POLL_MILLIS + OS_TCP_TIMEOUT` per hung call.

**Evidence:**
```java
// UpdaterClient:20 — no timeout
this.restClient = RestClient.builder().baseUrl(baseUrl).build();

// SelfAppUpdateExecutor:60 — blocks BEFORE deadline is established
resp = updaterClient.update(service, targetImage);

// SelfAppUpdateExecutor:71,80 — deadline checked only after hung call unblocks
long deadline = System.currentTimeMillis() + MAX_POLL_MILLIS;
while (System.currentTimeMillis() < deadline) {
    UpdaterStatus status = updaterClient.status(service); // can hang 2min per call
```

**Impact:**
A hung updater leaks async threads and renders the 15-minute self-update timeout meaningless, potentially keeping the self-update UI locked indefinitely.

**Proposed fix:**
Add `SimpleClientHttpRequestFactory` with `setReadTimeout(Duration.ofSeconds(5))` to `UpdaterClient`'s `RestClient`. Separately, move the deadline establishment to before the initial `update()` call and wrap it in a `Future.get(timeout)` so the absolute deadline is honored even on the trigger call.

**Effort:** SMALL

---

### Finding #12 — Uptime monitor sends crash notifications with no rate limit; restart loops generate email storms

**Severity:** MEDIUM
**Category:** RESILIENCE
**Blocks bug-analyzer:** NO

**What's wrong:**
`UptimeMonitorServiceImpl.checkAll()` sends `sendDownAlert(appName)` every time a RUNNING container is found stopped (line 46), with no per-app cooldown. A container in a crash-restart loop (crash → Docker auto-restart → crash) alternates between RUNNING and DOWN on successive 60-second checks. Each DOWN transition sends an email. Ten crashes per hour = ten emails per hour per app, with no deduplication.

**Evidence:**
```java
// UptimeMonitorServiceImpl:40-47
if (!isContainerRunning(app.getAppName())) {
    app.setStatus(DeploymentStatus.DOWN);
    deploymentRepository.save(app);
    notificationService.sendDownAlert(app.getAppName()); // fires every 60s the app is DOWN
}
```

**Impact:**
A restart-looping app floods the operator's inbox, making email alerts useless as a meaningful signal.

**Proposed fix:**
Add `private final ConcurrentHashMap<String, Long> lastAlertSentAt = new ConcurrentHashMap<>()` to `UptimeMonitorServiceImpl`. Before calling `sendDownAlert`, check `lastAlertSentAt.getOrDefault(appName, 0L)` and only send if more than 15 minutes have elapsed. Reset the entry on recovery. This is a two-line guard with no persistence requirement.

**Effort:** TRIVIAL

---

### Finding #13 — GitHub API RestClient has no timeout; `commits-ahead` endpoint blocks a request thread under GitHub degradation

**Severity:** MEDIUM
**Category:** RESILIENCE
**Blocks bug-analyzer:** NO

**What's wrong:**
`GitHubServiceImpl` builds its `RestClient` with no timeout (lines 31–33). `doCompare()` runs on the HTTP request thread from `DeploymentController.commitsAhead()` (line 167). If GitHub is slow or unreachable, the thread blocks for the OS TCP timeout. The 60-second in-memory cache limits exposure to one slow call per app per minute, but during a GitHub outage every cache-miss call blocks a thread. The error handler already returns a graceful fallback — the only missing piece is bounding the wait.

**Evidence:**
```java
// GitHubServiceImpl:31-33 — no connect or read timeout
this.client = RestClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader("Accept", "application/vnd.github+json")
        .build();
```

**Impact:**
A GitHub API outage ties up request threads proportional to the number of apps × dashboard poll frequency, degrading the UI for all users.

**Proposed fix:**
```java
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(Duration.ofSeconds(3));
factory.setReadTimeout(Duration.ofSeconds(5));
this.client = RestClient.builder()
        .baseUrl("https://api.github.com")
        .requestFactory(factory)
        .build();
```

**Effort:** TRIVIAL

---

### Finding #14 — `hardDeleteExpired()` runs a full-table `findAll()` inside a @Transactional every 60 seconds

**Severity:** MEDIUM
**Category:** RESILIENCE
**Blocks bug-analyzer:** NO

**What's wrong:**
`DeploymentServiceImpl.hardDeleteExpired()` (line 113) calls `deploymentRepository.findAll()` — a `SELECT *` with no `WHERE` clause — then filters in-memory for soft-deleted rows past their expiry. This runs inside a `@Transactional` method on a 60-second schedule, holding a DB connection for the full duration of the table scan plus any Docker stop calls in the loop. As deployment history grows the scan gets longer and blocks the shared connection pool proportionally.

**Evidence:**
```java
// DeploymentServiceImpl:113-125
@Scheduled(fixedDelay = 60_000)
@Transactional
public void hardDeleteExpired() {
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
    deploymentRepository.findAll().stream()      // ← full table scan every 60 seconds
            .filter(d -> d.getDeletedAt() != null && d.getDeletedAt().isBefore(cutoff))
            ...
}
```

**Impact:**
At current scale benign; becomes a progressive bottleneck as the deployment table grows, and holds a connection longer than needed on every scheduler tick.

**Proposed fix:**
Add `List<Deployment> findByDeletedAtIsNotNullAndDeletedAtBefore(LocalDateTime cutoff)` to `DeploymentRepository` and replace the `findAll()` call. One targeted query instead of a full scan; the `@Transactional` scope becomes correspondingly shorter.

**Effort:** TRIVIAL

---

## Session 3 — Schema and modularity

Files read (19 of 20 limit):
- Migrations V1–V13 (all 13 migration files)
- `UserAccount.java`, `EnvVar.java` (entities)
- `SecurityConfig.java`, `ApiKeyAuthFilter.java`
- `DeploymentRepository.java`, `DeploymentEventRepository.java`
- `application.properties`, `docker-compose.yml`

---

### Finding #15 — No `deployed_at` on `deployment` — no authoritative "last successful deploy" timestamp

**Severity:** HIGH
**Category:** SCHEMA
**Blocks bug-analyzer:** YES

**What's wrong:**
The `deployment` table has `created_at` (row creation) and `updated_at` (any change), but no column tracking when the current container was last successfully started. `updated_at` is written on every change: subdomain edits, status flips to DOWN and back, env var saves. A bug-analyzer trying to correlate crashes with deploy recency has no reliable signal — it would need to join `deployment_event` for the latest `DEPLOY_FINISHED` row's `finished_at`, which is an expensive lookup not backed by a targeted index.

**Evidence:**
```sql
-- V1__init.sql: only created_at/updated_at, no deployed_at
CREATE TABLE deployment (
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
-- V5 adds commit fields; no deployed_at in any migration
```

**Impact:**
Bug-analyzer cannot cheaply answer "how long has the current image been running" or "did this crash happen within the first N minutes of a deploy," which are the most useful signals for deploy-induced regressions.

**Proposed fix:**
1. Add migration: `ALTER TABLE deployment ADD COLUMN last_deployed_at TIMESTAMP;`
2. In `DeploymentServiceImpl`, after setting `status = RUNNING` on a successful deploy or restart, also set `deployment.setLastDeployedAt(LocalDateTime.now())`.
3. Map the new field on the `Deployment` entity.

**Effort:** SMALL

---

### Finding #16 — `env_var` rows are not deleted when a deployment is hard-deleted — encrypted secrets accumulate

**Severity:** HIGH
**Category:** SCHEMA
**Blocks bug-analyzer:** NO

**What's wrong:**
`DeploymentServiceImpl.hardDeleteExpired()` stops the container and calls `deploymentRepository.delete(d)` (line 121), but never deletes the corresponding `env_var` rows. Every env var stored for a deleted app stays in the database indefinitely. Since values are AES-encrypted (via `EncryptionService`), they are not plaintext, but they represent unnecessary secret material that survives the app's lifetime and can't be audited or rotated post-deletion.

**Evidence:**
```java
// DeploymentServiceImpl:116-124
deploymentRepository.findAll().stream()
    .filter(d -> d.getDeletedAt() != null && d.getDeletedAt().isBefore(cutoff))
    .forEach(d -> {
        dockerService.stopAndRemoveContainer(d.getAppName());
        deploymentRepository.delete(d);  // ← no env_var cleanup
    });
```
```sql
-- V2__add_env_var_table.sql: no FK, no CASCADE
CREATE TABLE env_var (app_name VARCHAR(100) NOT NULL, ...);
```

**Impact:**
Deleted apps leave encrypted secrets in the database permanently; the accumulation grows without bound as apps are cycled.

**Proposed fix:**
1. Inject `EnvVarRepository` into `DeploymentServiceImpl.hardDeleteExpired()` and add `envVarRepository.deleteByAppName(d.getAppName())` before `deploymentRepository.delete(d)`.
2. Add `void deleteByAppName(String appName)` to `EnvVarRepository`.
3. Optionally add an `ON DELETE CASCADE` FK from `env_var.app_name` → `deployment.app_name` as a safety net (see Finding #17).

**Effort:** SMALL

---

### Finding #17 — No FK constraints across tables — `deployment_event`, `env_var`, `pending_self_update` all join on free-text `app_name`

**Severity:** HIGH
**Category:** SCHEMA
**Blocks bug-analyzer:** YES

**What's wrong:**
Every inter-table relationship uses `app_name VARCHAR` with no FK constraint. `deployment.app_name` has a UNIQUE constraint (V4) and could be a FK target. Without FK constraints: (a) orphaned rows accumulate silently (env_var after hard delete — Finding #16; events for apps that no longer exist); (b) a bug-analyzer joining `deployment` ↔ `deployment_event` on `app_name` has no database-level guarantee of consistency; (c) ON DELETE behavior is completely undefined.

**Evidence:**
```sql
-- No FK found in any migration V1–V13. Examples:
-- V6: deployment_event.app_name is just VARCHAR(100), no REFERENCES
-- V2: env_var.app_name is just VARCHAR(100), no REFERENCES
-- V10: pending_self_update.app_name is just VARCHAR(100), no REFERENCES
```

**Impact:**
Referential integrity is entirely application-enforced; schema drift (hard delete without cleanup) silently creates orphaned rows that corrupt bug-analyzer queries.

**Proposed fix:**
Add a V14 migration with targeted FK constraints and ON DELETE semantics:
```sql
-- Preserve history; events survive app deletion
ALTER TABLE deployment_event
    ADD CONSTRAINT fk_event_app FOREIGN KEY (app_name)
    REFERENCES deployment (app_name) ON DELETE NO ACTION DEFERRABLE;

-- Secrets should be cleaned up with the app
ALTER TABLE env_var
    ADD CONSTRAINT fk_envvar_app FOREIGN KEY (app_name)
    REFERENCES deployment (app_name) ON DELETE CASCADE;

-- Pending updates are moot without the app
ALTER TABLE pending_self_update
    ADD CONSTRAINT fk_pending_app FOREIGN KEY (app_name)
    REFERENCES deployment (app_name) ON DELETE CASCADE;
```
Decision needed: `deployment_event ON DELETE NO ACTION` means you cannot hard-delete a deployment that still has events. Consider `SET NULL` on `app_name` instead if you want to preserve event history after app deletion.

**Effort:** SMALL

---

### Finding #18 — No event bus or Postgres NOTIFY — bug-analyzer can only poll `deployment_event`

**Severity:** HIGH
**Category:** MODULARITY
**Blocks bug-analyzer:** YES

**What's wrong:**
All inter-service communication in the backend is synchronous REST. There is no Postgres `LISTEN/NOTIFY`, no outbox table, no message broker (Kafka/RabbitMQ), and no webhook/callback mechanism on `deployment_event` inserts. A bug-analyzer that needs to react to crash events (`CRASHED`), failed deploys (`FAILED`), or completed deploys (`DEPLOY_FINISHED`) has no way to subscribe — it must poll the `deployment_event` table periodically by querying `WHERE created_at > :lastSeen ORDER BY created_at ASC`.

**Evidence:**
```yaml
# docker-compose.yml — no broker service, just postgres + traefik + ollama
services:
  postgres:   # no NOTIFY trigger defined anywhere
  traefik:
  ollama:
  launchpad:
  launchpad-frontend:
  launchpad-updater:
```

**Impact:**
Bug-analyzer integration requires polling, introducing latency and additional query load on Postgres; the polling cursor must survive bug-analyzer restarts, adding state management complexity.

**Proposed fix:**
Smallest-change option requiring no new infrastructure: add a Postgres trigger on `deployment_event` INSERT that calls `NOTIFY launchpad_events, <payload>`. The bug-analyzer uses a persistent connection with `LISTEN launchpad_events` to receive near-real-time notifications. The `deployment_event` table already has all needed fields; the trigger just fans out the insert.

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

**Effort:** SMALL

---

### Finding #19 — No shared module for event types, DTOs, or entity definitions — bug-analyzer must duplicate or infer them

**Severity:** HIGH
**Category:** MODULARITY
**Blocks bug-analyzer:** YES

**What's wrong:**
`DeploymentEventType`, `DeploymentEventStatus`, `TriggerSource`, and `DeploymentStatus` are all defined as Java enums in `com.filipnikolov.launchpad.deployment.model`, tightly coupled to the Spring Boot app. `DeploymentEventDto` lives in the same package. There is no separate shared artifact (Maven module, published jar, OpenAPI spec, or proto file) that a second service could depend on to get canonical type definitions. A Java bug-analyzer would copy-paste the enums; a non-Java service would hardcode string values derived from reading the source.

**Evidence:**
```
backend/src/main/java/com/filipnikolov/launchpad/deployment/model/
  DeploymentEventType.java      (22 values)
  DeploymentEventStatus.java
  DeploymentStatus.java
  TriggerSource.java
backend/src/main/java/com/filipnikolov/launchpad/deployment/dto/
  DeploymentEventDto.java
```
No `shared/`, `common/`, or `api/` module exists in the repo.

**Impact:**
Any addition to `DeploymentEventType` in the backend requires a manual update in the bug-analyzer; enum drift causes silent event filtering failures.

**Proposed fix:**
Create a `launchpad-events` Maven module at the repo root containing only the event-type enums, the `DeploymentEventDto`, and a small `PlatformEvent` wrapper record. Both `launchpad-backend` and `bug-analyzer` declare it as a `<dependency>` with `<scope>compile</scope>`. No Spring annotations, no JPA — pure Java. Alternatively, publish an OpenAPI spec for the `deployment_event` REST endpoint and generate client DTOs from it.

**Effort:** MEDIUM

---

### Finding #20 — `user_account.api_key_hash` is never read by auth — dead column or unimplemented multi-user feature

**Severity:** MEDIUM
**Category:** SCHEMA
**Blocks bug-analyzer:** NO

**What's wrong:**
`ApiKeyAuthFilter` authenticates every `/api/**` request by comparing the `X-API-Key` header against the single `app.api-key` env var (loaded once at startup). It never queries `user_account`. The `api_key_hash` column added in V7 suggests per-user API keys were planned, but the implementation was never wired up. As a result, every caller uses the same shared secret with no per-identity audit trail.

**Evidence:**
```java
// ApiKeyAuthFilter:46 — compares against env var, not DB
if (providedKey != null && constantTimeEquals(apiKey, providedKey)) {
    var auth = new UsernamePasswordAuthenticationToken("api-client", null, List.of());
    // ↑ principal is always the same literal "api-client", never per-user
```
```sql
-- V7: api_key_hash exists but is never SELECTed
CREATE TABLE user_account (api_key_hash VARCHAR(255) NOT NULL, ...);
```

**Impact:**
No per-user API keys means the bug-analyzer and the frontend share the same credential; revoking or rotating access for one requires rotating for all.

**Proposed fix:**
Decide: (a) if per-user keys are not planned, remove `api_key_hash` from `user_account` (add a V14 migration to drop the column) and document the single-key model; (b) if per-user keys are planned, implement `UserAccountRepository.findByApiKeyHash(hash)` in `ApiKeyAuthFilter` and replace the env-var comparison. For bug-analyzer, option (a) is fine — it just uses the shared key.

**Effort:** SMALL

---

### Finding #21 — `deployment.commit_message` is `VARCHAR(500)` — long commit messages cause a hard DB error

**Severity:** MEDIUM
**Category:** SCHEMA
**Blocks bug-analyzer:** NO

**What's wrong:**
Migration V5 adds `commit_message VARCHAR(500)` to the `deployment` table. Commit messages with a body or footer (conventional commits, merge commits) routinely exceed 500 characters. When the webhook sends a message over 500 chars, Postgres throws `value too long for type character varying(500)`, which propagates as an exception through `createDeployment`, setting the deployment status to `FAILED` with the error message. The `deployment_event` table correctly uses `TEXT` for the same field, making the inconsistency an easy migration to fix.

**Evidence:**
```sql
-- V5: VARCHAR(500)
ALTER TABLE deployment ADD COLUMN commit_message VARCHAR(500);
-- V6: TEXT (correct)
CREATE TABLE deployment_event (commit_message TEXT, ...);
```
```java
// Deployment.java:35
@Column(length = 500)
private String commitMessage;
```

**Impact:**
Any deploy triggered by a commit with a long message fails entirely, even though the image and container are perfectly valid.

**Proposed fix:**
Add migration: `ALTER TABLE deployment ALTER COLUMN commit_message TYPE TEXT;` and remove `@Column(length = 500)` from `Deployment.commitMessage`.

**Effort:** TRIVIAL

---

### Finding #22 — Two missing indexes: uptime monitor hot query and event-type lookup

**Severity:** MEDIUM
**Category:** SCHEMA
**Blocks bug-analyzer:** YES

**What's wrong:**
Two queries run frequently with no covering index. (1) `UptimeMonitorServiceImpl` calls `findByStatusAndDeletedAtIsNull(RUNNING)` every 60 seconds. The current `idx_deployment_status(status)` is used but forces a filter pass over all rows to exclude `deleted_at IS NOT NULL`. A partial index restricted to live rows would eliminate the filter. (2) `DeploymentEventServiceImpl.latestForApp()` calls `findTopByAppNameAndEventTypeOrderByCreatedAtDesc(appName, DEPLOY_FINISHED)`, which needs `(app_name, event_type, created_at DESC)` but the existing `idx_app_created(app_name, created_at DESC)` doesn't include `event_type` — Postgres must scan all events for the app and filter on type. A bug-analyzer running similar queries per crash event would hit the same gap.

**Evidence:**
```sql
-- V4: covers status but not the deleted_at predicate
CREATE INDEX idx_deployment_status ON deployment (status);
-- V6: covers (app_name, created_at) but not event_type
CREATE INDEX idx_app_created ON deployment_event (app_name, created_at DESC);
```

**Impact:**
At current data volume both queries are fast regardless; as event history grows (hundreds of events per app) the type-lookup degrades linearly and becomes the bottleneck for every activity feed render.

**Proposed fix:**
Add a V14 migration:
```sql
-- Replaces idx_deployment_status for the monitor's common case
CREATE INDEX idx_deployment_status_live
    ON deployment (status) WHERE deleted_at IS NULL;

-- Covers the latestForApp + analogous bug-analyzer queries
CREATE INDEX idx_event_app_type_created
    ON deployment_event (app_name, event_type, created_at DESC);
```

**Effort:** TRIVIAL

---

## Session 4 — Logs and notifications

Files read (8 of 12 limit):
- `frontend/src/hooks/useBuildLogs.ts`
- `frontend/src/hooks/useEvents.ts`
- `frontend/src/hooks/useDeployProgressNotifier.ts`
- `frontend/src/lib/proxy.ts`
- `frontend/src/app/api/apps/[appName]/logs/runtime/route.ts`
- `DeploymentEventController.java`
- `AiController.java`
- `NotificationService.java` (interface)

Note: `RuntimeLogController.java`, `DockerServiceImpl.java`, `NotificationServiceImpl.java`,
and `UptimeMonitorServiceImpl.java` were read in Sessions 1–2 and are not re-read here.

---

### Finding #23 — SSE log stream reconnects every 3 seconds after container stops — tight loop against Docker API

**Severity:** HIGH
**Category:** BUG
**Blocks bug-analyzer:** NO

**What's wrong:**
`DockerServiceImpl.streamContainerLogs` sets `withFollowStream(true)` (line 187). When a container stops, the Docker daemon closes the log stream, firing `ResultCallback.onComplete()` → `emitter::complete`. The frontend `EventSource` then enters its default 3-second reconnect cycle. Each reconnect opens a new Docker log stream on the backend; that stream immediately closes because the container is stopped; the SSE connection closes; the browser reconnects again. This loop runs indefinitely at 3-second intervals — one Docker API call per interval per open browser tab — until the user navigates away. There is no `retry:` SSE directive from the backend and no stopped-container guard at the route level.

**Evidence:**
```java
// DockerServiceImpl:187-188 — follow=true, no guard for stopped containers
return dockerClient.logContainerCmd(containerName)
        .withFollowStream(true)
        ...
        .exec(new ResultCallback.Adapter<Frame>() { ... });
```
```ts
// useBuildLogs.ts:17-22 — EventSource reconnects every ~3s by default on close
const es = new EventSource(`/api/apps/${encodeURIComponent(appName)}/logs/runtime`);
es.onerror = () => setStatus("error");
```

**Impact:**
Each stopped container with an open log viewer generates a Docker API call every 3 seconds; under load (multiple apps, multiple tabs) this hammers the Docker daemon and inflates its connection count.

**Proposed fix:**
Two complementary changes: (1) In `RuntimeLogController.streamLogs`, detect up front whether the container is running via `dockerService.isContainerRunning(appName)`; if not, send a single `retry: 60000` SSE directive before completing the emitter — this throttles the browser's reconnect to 60 seconds. (2) In the Docker `ResultCallback.onComplete`, emit a terminal SSE comment (e.g., `event: done\ndata: container-stopped\n\n`) before calling `emitter.complete()` so the frontend can distinguish a clean stop from a network error and stop retrying.

**Effort:** SMALL

---

### Finding #24 — AI log analysis blocks a Tomcat thread for up to 120 seconds; no streaming and no per-app guard

**Severity:** MEDIUM
**Category:** FLOW
**Blocks bug-analyzer:** NO

**What's wrong:**
`AiController.analyzeLogs` (lines 68–78) performs two sequential blocking operations on the request thread: (1) `dockerService.getContainerLogs(appName, 200)` — a synchronous Docker log fetch that calls `awaitCompletion()`; (2) `ollamaService.analyzeLog(joined)` — a synchronous HTTP call to Ollama with a 120-second read timeout. Neither is async, streamed, or wrapped in a `CompletableFuture`. A single analysis request can tie up a Tomcat thread for up to ~120 seconds; concurrent requests from multiple users hold multiple threads for the same duration. There is no queue or concurrency guard to prevent parallel Ollama calls saturating the model.

**Evidence:**
```java
// AiController:68-78
List<String> logLines = dockerService.getContainerLogs(appName, logTailLines); // blocks
String joined = String.join("\n", logLines);
return ResponseEntity.ok().body(ollamaService.analyzeLog(joined)); // blocks up to 120s
```
```properties
# application.properties
launchpad.ai.request-timeout-seconds=120
```

**Impact:**
The frontend shows a spinner with no progress feedback for up to 2 minutes; under concurrent use, analysis requests exhaust the thread pool before other API calls are affected.

**Proposed fix:**
1. Short-term: add a semaphore (`Semaphore(1)`) to `OllamaServiceImpl` to serialize Ollama calls; return `503` immediately if the semaphore cannot be acquired, so the frontend can show "analysis in progress, try again shortly" rather than a stuck spinner.
2. Long-term: switch `analyzeLogs` to `SseEmitter` and stream the Ollama response token-by-token using Ollama's streaming API (`stream: true` in the request body) — Ollama's `/api/generate` supports this and the `OllamaResponse` model would need to handle incremental tokens.

**Effort:** MEDIUM

---

### Finding #25 — Deploy progress detected via 10-second polling; DEPLOY_FINISHED events are stale by up to 10 seconds

**Severity:** MEDIUM
**Category:** FLOW
**Blocks bug-analyzer:** NO

**What's wrong:**
`useEvents` (lines 32–42) polls `/api/events` every 10 seconds via SWR `refreshInterval: 10_000`. `useDeployProgressNotifier` consumes this hook to show deploy progress toasts. A deploy that completes in 30 seconds will show the "Deployed" success toast anywhere from 0–10 seconds after completion depending on when the next poll fires. There is no push mechanism — the activity feed and deploy toasts are driven entirely by this poll cycle. The same 10-second window applies to FAILED events: the user sees an error toast up to 10 seconds after the deploy failure is recorded.

**Evidence:**
```ts
// useEvents.ts:32-42
const { data, error, isLoading, mutate } = useSWR<DeploymentEvent[]>(
    url, fetcher,
    { refreshInterval: 10_000, ... }  // 10-second poll
);
```
```ts
// useDeployProgressNotifier.ts:36 — reads from the same 10-second poll
const { events } = useEvents({ limit: 30 });
```

**Impact:**
Today this is a UX latency issue; it becomes a correctness gap for the planned SSE log streaming migration — logs stream live but deploy status lags 10 seconds behind, creating a confusing inconsistency where logs show "server started" but the toast still says "Deploying…".

**Proposed fix:**
The backend `deployment_event` table is already the source of truth. Replace the 10-second poll with an SSE endpoint on the backend (e.g., `GET /api/events/stream`) that pushes new `deployment_event` rows as they are inserted. The frontend replaces `useSWR` with `EventSource`. This aligns with the Postgres `NOTIFY` trigger proposed in Finding #18 — the backend SSE endpoint would `LISTEN` on that channel and forward events to connected clients.

**Effort:** LARGE (needs backend SSE endpoint + frontend hook rewrite; blocked on Finding #18)

---

### Finding #26 — `NotificationService` interface has one method; three required alert types have no contract

**Severity:** HIGH
**Category:** BUG
**Blocks bug-analyzer:** NO

**What's wrong:**
The `NotificationService` interface declares only `sendDownAlert(String appName)`. Three additional alert types are required by the system design but have no method signature anywhere: deploy failed (deployment finishes with `FAILED` status), container crashed (detected by uptime monitor), and uptime recovered (container goes from DOWN back to RUNNING). Because the interface doesn't declare these methods, `NotificationServiceImpl` has no obligation to implement them, there is no compilation guard if they are added later inconsistently, and mock implementations (for tests) will never be forced to cover them. Crash and recovery notifications were each partially identified in Findings #3 and #4 — this finding captures the root-cause: the interface itself is incomplete.

**Evidence:**
```java
// NotificationService.java:7-15 — only one method
public interface NotificationService {
    void sendDownAlert(String appName);  // the only notification that exists
    // missing: sendDeployFailedAlert, sendCrashedAlert, sendRecoveredAlert
}
```

**Impact:**
Any developer adding a new notification type can bypass the interface and call Resend directly, creating multiple inconsistent codepaths for email delivery.

**Proposed fix:**
Extend the interface immediately, even if the implementations are stubs initially:
```java
public interface NotificationService {
    void sendDownAlert(String appName);
    void sendRecoveredAlert(String appName);
    void sendDeployFailedAlert(String appName, String errorMessage);
    void sendCrashedAlert(String appName);
}
```
Then implement each in `NotificationServiceImpl` using the same Resend `restClient` pattern as `sendDownAlert`. Wire `sendDeployFailedAlert` into the `FAILED` branch of `DeploymentServiceImpl.createDeployment` (line 90), `sendCrashedAlert` into `UptimeMonitorServiceImpl` when status changes to DOWN, and `sendRecoveredAlert` into the recovery branch.

**Effort:** SMALL

---

### Finding #27 — Next.js log proxy `fetch` has no timeout; backend hang holds a Node.js server thread indefinitely

**Severity:** MEDIUM
**Category:** RESILIENCE
**Blocks bug-analyzer:** NO

**What's wrong:**
The Next.js log route (`/api/apps/[appName]/logs/runtime/route.ts`, line 31) calls `fetch(backend + "/api/apps/…/logs", { signal: req.signal })` with no `AbortSignal` timeout beyond what the browser sends. The `req.signal` fires when the browser's `EventSource` closes — but the browser does not close the `EventSource` immediately; it waits for the response to start before firing `onerror`. If the backend is slow to respond (e.g., Docker daemon is unreachable — Finding #8), the `fetch` call hangs waiting for the first byte. During this time the Node.js request handler is blocked. Many concurrent clients opening the log viewer can exhaust the Node.js thread pool before the backend responds or the OS TCP timeout expires.

**Evidence:**
```ts
// runtime/route.ts:31-38 — no AbortSignal timeout
const upstream = await fetch(
    `${backend}/api/apps/${encodeURIComponent(params.appName)}/logs`,
    {
        headers: { "X-API-Key": apiKey, Accept: "text/event-stream" },
        cache: "no-store",
        signal: req.signal,   // ← only the browser-disconnect signal, no deadline
    },
);
```

**Impact:**
When the Docker daemon is slow (the same scenario as Finding #8), the backend SSE endpoint stalls, and the Next.js proxy accumulates blocked server threads until Node.js becomes unresponsive.

**Proposed fix:**
Compose the browser's abort signal with a connect-phase deadline:
```ts
const connectTimeout = AbortSignal.timeout(10_000); // 10s to get first byte
const combined = AbortSignal.any([req.signal, connectTimeout]);
const upstream = await fetch(`${backend}/api/apps/…/logs`, {
    headers: { "X-API-Key": apiKey, Accept: "text/event-stream" },
    cache: "no-store",
    signal: combined,
});
```
This bounds the time before the first SSE byte arrives; once streaming begins, the browser's signal governs the lifetime.

**Effort:** TRIVIAL

---

## Session 5 — Frontend data layer

**Files read this session:** `useApps.ts`, `useContainerStats.ts`, `useCommitsAhead.ts`, `usePreferences.ts`, `useToast.tsx`, `page.tsx`, `auth.ts`, `types/deployment.ts`

---

### Finding #28 — No error boundary on the root page

**Severity:** MEDIUM
**Blocks bug-analyzer:** NO

**Evidence:**
```tsx
// page.tsx
<Suspense fallback={null}>
  <AppGrid />
</Suspense>
```
No `ErrorBoundary` wraps `AppGrid` or the page root. Any unhandled render-time exception (including one caused by a SWR response returning unexpected shape) crashes the entire dashboard with a Next.js unhandled error screen rather than showing a degraded UI.

**Impact:**
A single malformed API response silently destroys the dashboard for the user's session. On a single-user PaaS this is low frequency but high pain.

**Proposed fix:**
Wrap the page body in a simple `ErrorBoundary` component that renders an inline error card instead of a blank screen:
```tsx
<ErrorBoundary fallback={<ErrorCard message="Dashboard failed to load" />}>
  <Suspense fallback={null}>
    <AppGrid />
  </Suspense>
</ErrorBoundary>
```

**Effort:** TRIVIAL

---

### Finding #29 — `useContainerStats` silently swallows SWR errors

**Severity:** LOW
**Blocks bug-analyzer:** NO

**Evidence:**
```ts
// useContainerStats.ts
const { data } = useSWR<ContainerStats>(key, fetcher, { refreshInterval: 5_000 });
return { stats: data };  // SWR error field never returned
```
The hook returns only `stats`, discarding the SWR `error` field. Consumers have no way to distinguish "stats not yet fetched" from "stats fetch failed." The CPU/memory panel shows nothing and stays silent on daemon unreachability.

**Impact:**
When the Docker daemon is down (Finding #8 scenario), the stats panel goes blank with no visual signal to the user that a connection problem exists.

**Proposed fix:**
Return the error field and let the panel render a small error indicator:
```ts
const { data, error } = useSWR<ContainerStats>(key, fetcher, { refreshInterval: 5_000 });
return { stats: data, error };
```

**Effort:** TRIVIAL

---

### Finding #30 — `useApps` returns `undefined` while loading, not `[]`

**Severity:** LOW
**Blocks bug-analyzer:** NO

**Evidence:**
```ts
// useApps.ts
const { data, error, isLoading } = useSWR<App[]>(…);
return { apps: data, error, isLoading };
// data is undefined until the first fetch completes
```
`apps` is typed as `App[] | undefined`. Every consumer must null-check before iterating. If any consumer forgets, a runtime TypeError occurs during the initial load window.

**Impact:**
Low probability of crash (TypeScript should catch it), but it is a defensive-API smell that makes adding new consumers error-prone.

**Proposed fix:**
```ts
return { apps: data ?? [], error, isLoading };
```

**Effort:** TRIVIAL

---

### Finding #31 — User preference keys have no backend implementation (silent contract mismatch)

**Severity:** MEDIUM
**Blocks bug-analyzer:** NO

**Evidence:**
```ts
// usePreferences.ts — DEFAULT_PREFS
const DEFAULT_PREFS = {
  notify_on_fail: true,
  notify_on_crash: true,
  notify_on_first_deploy: true,
  notify_on_rollback: false,
  // …
};
```
```java
// NotificationServiceImpl.java — only method
public void sendDownAlert(String appName) { … }
```
The frontend exposes toggles for deploy-failure, crash, first-deploy, and rollback notifications. The backend `NotificationService` has exactly one method — `sendDownAlert`. None of the other four notification categories are implemented on the backend. The user can toggle these preferences and they will be persisted, but the corresponding emails will never fire.

**Impact:**
Users believe they have configured alerts. The alerts silently do nothing. When notifications are expanded this will require auditing which preference keys were actually wired up vs. which were orphaned.

**Proposed fix:**
Either implement the missing notification methods on the backend (the correct long-term path), or remove the frontend toggles for unimplemented categories until the backend is ready. Add a comment in `NotificationServiceImpl` listing the unimplemented events so the gap is visible during future work.

**Effort:** MEDIUM (backend wiring) / TRIVIAL (remove dead toggles short-term)

---

### Finding #32 — No timeout on frontend `fetch()` calls

**Severity:** LOW
**Blocks bug-analyzer:** NO

**Evidence:**
```ts
// Multiple hooks — e.g., useApps.ts SWR fetcher
const fetcher = (url: string) => fetch(url).then(r => r.json());
```
No `AbortSignal.timeout()` is passed to any SWR fetcher. If the backend hangs (connection established but no response body), the fetch pends until the browser's default timeout (~5 min in most environments). SWR's `refreshInterval` will schedule another fetch before the first resolves, potentially stacking inflight requests.

**Impact:**
Under a slow backend, the browser accumulates stale inflight requests. This compounds with Finding #8 (no Docker timeout) and Finding #25 (no Next.js proxy timeout): a single Docker daemon stall can cascade from backend → Next.js proxy → browser.

**Proposed fix:**
Add a timeout signal to each SWR fetcher:
```ts
const fetcher = (url: string) =>
  fetch(url, { signal: AbortSignal.timeout(15_000) }).then(r => r.json());
```

**Effort:** TRIVIAL

---

### Finding #33 — Toast stack capped at 3; concurrent deploy events silently drop earlier toasts

**Severity:** LOW
**Blocks bug-analyzer:** NO

**Evidence:**
```ts
// useToast.tsx
setToasts(prev => [...prev, newToast].slice(-3));
```
Only the three most-recent toasts are kept. If four apps deploy simultaneously, the first app's progress toast is silently discarded. The `useDeployProgressNotifier` hook tracks `toastId` per app; if that toast was dropped, `toast.update()` targets a nonexistent ID and the success/failure notification is also silently lost.

**Impact:**
On a busy Launchpad instance managing many apps, concurrent deploys produce a silent notification gap. The user sees toasts for apps 2–4 but never learns whether app 1 succeeded or failed.

**Proposed fix:**
Either raise the cap (5–6 is safe for the current layout) or, for persistent progress toasts specifically, skip the slice so they are never evicted:
```ts
const persistentCount = prev.filter(t => t.persistent).length;
const newList = [...prev, newToast];
return newList.length > 3 + persistentCount
  ? [newList[0], ...newList.slice(-(2 + persistentCount))]
  : newList;
```

**Effort:** LOW

---

## What NOT to refactor

### 1. ActionLockService (in-memory mutex)
The in-memory `ReentrantLock` approach is correct for a single-node PaaS. Replacing it with a distributed lock (Redis Redlock, Postgres advisory lock) before the system ever needs horizontal scaling would be premature. The current design is simple, auditable, and has zero external dependencies. **Leave it.**

### 2. Custom HMAC session tokens in `auth.ts`
The hand-rolled HMAC-SHA256 session token is short, auditable, and has no library surface area to update. Replacing it with NextAuth or a JWT library adds a dependency and a much larger attack surface for a single-user dashboard. The `Secure`-in-production flag is correct. **Leave it.**

### 3. Flyway migration naming (V1–V13 sequential)
The sequential numbering is conventional and works correctly with the current single-developer cadence. Switching to timestamp-based migration names would require renaming all existing migrations and gains nothing. **Leave it.**

### 4. SWR for data fetching
SWR's stale-while-revalidate pattern is well-matched to a dashboard that needs background refresh without complexity. Replacing it with React Query or a custom fetch layer would be a lateral move with no meaningful benefit at this scale. The only fix needed is returning `data ?? []` defaults and adding timeouts (Findings #30, #32) — not replacing the library. **Keep SWR, fix the gaps.**

### 5. Traefik as the reverse proxy
Traefik's Docker label–based configuration is exactly right for a Docker Compose PaaS. Replacing it with Nginx or Caddy would require manual config updates for every new app and lose the dynamic routing model that is central to how Launchpad works. **Leave it.**

---

## Execution order recommendation

Findings are ordered by: (1) risk of data loss or silent failure, (2) blocks future work, (3) effort.

### Phase 1 — Stop the bleeding (do before any other refactor)

| # | Finding | Why first |
|---|---------|-----------|
| #8 | Docker pull has no timeout — infinite latch.await() | JVM thread leak; can hang the entire backend |
| #1 | createDeployment() holds DB connection across Docker ops | HikariCP exhaustion; makes the system fail under any load |
| #25 | No timeout on NotificationService HTTP call | Scheduler thread starvation on Resend outage |
| #11 | DockerClientConfig has no connection/response timeout | Silent hang; amplifies #8 and #1 |

**All four are TRIVIAL or LOW effort. Fix in a single PR.**

### Phase 2 — Data integrity and correctness

| # | Finding | Why second |
|---|---------|-----------|
| #5 | commit_message VARCHAR(500) truncates silently | Data loss on long messages; simple ALTER TABLE |
| #13 | No FK constraints across the schema | Orphan rows accumulate silently; add FKs before schema expands |
| #2 | DeployHookController injects Repository directly | Layering violation; fix before adding bug-analyzer webhook path |
| #16 | pinned-image check is non-transactional | Race condition that pins wrong image under concurrent webhooks |

### Phase 3 — Observability gaps

| # | Finding | Why third |
|---|---------|-----------|
| #18 | UptimeMonitor records no CRASHED event | crash events needed by bug-analyzer; add before wiring the microservice |
| #19 | Recovery from DOWN records no event and sends no notification | symmetry with crash alerting; users are blind to recovery |
| #31 | Preference keys with no backend implementation | Implement or remove before expanding notification surface |
| #28 | No ErrorBoundary on root page | Cheap protection; do with the next frontend PR |

### Phase 4 — Resilience hardening

| # | Finding | Why fourth |
|---|---------|-----------|
| #7 | hardDeleteExpired() does findAll() (full table scan) | Performance degrades as deployment history grows |
| #9 | SseEmitter timeout is 0 (infinite) | Zombie emitters accumulate on client disconnect |
| #10 | useBuildLogs reconnects on stopped-container errors | Infinite EventSource loop burns CPU |
| #3 | DeployHookController blocks until deploy complete | Webhook caller times out on slow images; make async |
| #32 | No fetch() timeout in SWR fetchers | Silent request stacking in the browser |
| #29 | useContainerStats swallows errors | No visual signal when Docker is unreachable |

### Phase 5 — Bug-analyzer prerequisites (block on these)

| # | Finding | Why last |
|---|---------|-----------|
| #13 | FK constraints (app_name references) | Schema must be consistent before adding a subscriber microservice |
| #18 | CRASHED event emission | bug-analyzer needs this event to trigger |
| #20 | `user_account` table orphaned from auth | Decide: wire up or drop before adding new services that may need auth |
| #14 | pending_self_update has no FK | Clean up before schema stabilizes |

### What to skip entirely
- Finding #17 (`POSTGRES_DB` = username footgun in docker-compose): document it, don't change it — changing it requires a volume wipe.
- Finding #6 (GlobalExceptionHandler maps RuntimeException→500): the current mapping is correct; do not over-granularize error types until the refactor is underway.
- Finding #33 (toast cap at 3): raise the cap to 5 in the same PR as any other frontend fix; not worth its own PR.

