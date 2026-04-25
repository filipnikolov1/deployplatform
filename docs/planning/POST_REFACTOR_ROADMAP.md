# Post-refactor roadmap — everything else

**Status:** planned, not started
**Scope:** everything beyond the core backend refactor (`REFACTOR_PLAN.md`) and Time Machine (`TIME_MACHINE_PLAN.md`)

> **Amendment applied 2026-04-24** — this plan was updated per `CONTEXT_CHECK.md` findings. Key changes: Track D SSE scope clarified (log SSE exists; event SSE is what's missing); full env var list with `VECTOR_ENCRYPTION_KEY` flagged as critical. See `PLAN_AMENDMENTS.md` for the full audit trail.

This is the "what else needs to happen" document. It ties together the rename, the monorepo restructure, the event stream SSE, the Gemini migration, and flagged future work. Some items are near-term (weeks), some are multi-semester (the iOS app, the fine-tuned model).

---

## 1. Global sequencing

These tracks have real dependencies on each other. Doing them in the wrong order creates merge hell.

```
Vector rename + monorepo restructure       [1 day]
    ↓
Core backend refactor (7 phases)           [6-8 days, per REFACTOR_PLAN.md]
    ↓
AiProvider / Gemini migration              [2-3 days]
    ↓
Event stream SSE (diagnose + fix)          [2-3 days]
    ↓
Time Machine (7 phases)                    [3-4 weeks, per TIME_MACHINE_PLAN.md]
    ↓
Theme upgrade                              [12-15 hrs, per THEME_UPGRADE.md]
    ↓
[ready for future features: iOS, ML fine-tune, more microservices]
```

Rename goes first because renaming is cheaper on an unchanged tree than on a refactored one. Refactor is second because it unblocks everything. Gemini and SSE slot in before Time Machine because Time Machine depends on both. Theme comes after Time Machine so the theme migration picks up the Time Machine UI too.

Do not interleave. Finish each track before starting the next.

---

## 2. Track A — Vector rename + monorepo restructure

**Estimated effort:** 1 focused day
**Prerequisites:** none (do this first, on a clean working tree)

### Goals

1. Repo root becomes `vector-platform/` (the monorepo)
2. Each service / module gets its `vector-*` name
3. Introduce multi-module Maven at the root
4. Add the shared module skeletons (empty for now — filled in later tracks)
5. All Docker images, compose services, and env vars get renamed

### Current → new folder mapping

```
launchpad/                 →  vector-platform/
├── backend/               →  ├── vector-api/
├── frontend/              →  ├── vector-web/
├── updater/               →  ├── vector-updater/
├── docs/                  →  ├── docs/               (unchanged)
├── (new)                  →  ├── vector-events/      (empty skeleton)
├── (new)                  →  ├── vector-ai/          (empty skeleton)
├── (new)                  →  ├── vector-analyzer/    (empty, created in Time Machine Phase 1)
├── (new)                  →  ├── pom.xml             (parent POM)
├── .env, .env.example     →  ├── .env, .env.example  (update var names)
├── docker-compose.yml     →  ├── docker-compose.yml  (update service names + images)
├── README.md              →  ├── README.md           (rewrite for Vector)
├── REFACTOR_PLAN.md       →  ├── docs/planning/REFACTOR_PLAN.md  (move)
├── REVIEW.md              →  ├── docs/planning/REVIEW.md
├── THEME_EXPORT.md        →  ├── docs/planning/THEME_EXPORT.md
└── THEME_UPGRADE.md       →  └── docs/planning/THEME_UPGRADE.md
```

### Service name changes

| Old | New | Why |
|---|---|---|
| `launchpad-backend` | `vector-api` | Clearer purpose, future-proof when iOS/macOS clients exist |
| `launchpad-frontend` | `vector-web` | Signals "web client" specifically |
| `launchpad-updater` | `vector-updater` | Prefix consistency |
| (will create) | `vector-analyzer` | New microservice for Time Machine |
| Java package `com.filipnikolov.launchpad` | `dev.filipnikolov.vector` | Cleaner namespace; matches `filipnikolov.dev` domain |

### Parent POM structure

Top-level `pom.xml` orchestrates all Java modules:

```xml
<project>
    <groupId>dev.filipnikolov.vector</groupId>
    <artifactId>vector-platform</artifactId>
    <version>0.1.0</version>
    <packaging>pom</packaging>

    <modules>
        <module>vector-events</module>
        <module>vector-ai</module>
        <module>vector-api</module>
        <module>vector-analyzer</module>
        <module>vector-updater</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-boot.version>3.x.x</spring-boot.version>
        <!-- etc -->
    </properties>

    <dependencyManagement>
        <!-- pin Spring Boot BOM, Jackson BOM, etc. for consistency -->
    </dependencyManagement>
</project>
```

Each module's `pom.xml` declares `<parent>` pointing to the root, and adds only its own dependencies. `vector-api` and `vector-analyzer` declare `<dependency>` on `vector-events` and (eventually) `vector-ai`.

`vector-web` is not a Maven module — it's Node.js. It stays a sibling folder.

### Docker build strategy

Multi-stage Dockerfiles per service, each running Maven build from the monorepo root:

```dockerfile
# vector-api/Dockerfile — build command: docker build -f vector-api/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY vector-events/ vector-events/
COPY vector-ai/ vector-ai/
COPY vector-api/ vector-api/
RUN mvn -pl vector-api -am clean package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /src/vector-api/target/vector-api-*.jar /app/vector-api.jar
ENTRYPOINT ["java", "-jar", "/app/vector-api.jar"]
```

The `-pl vector-api -am` flag builds vector-api and all its dependent modules (`-am` = "also make"). Each service image rebuilds the shared modules, which is fine at this scale.

### docker-compose.yml updates

- Service names: `backend` → `vector-api`, `frontend` → `vector-web`, `updater` → `vector-updater` (plus new `vector-analyzer` added later)
- Image names: `launchpad-backend` → `vector-api`, etc.
- Container names: same pattern
- Environment variable prefixes: `LAUNCHPAD_*` → `VECTOR_*`
- Traefik labels update to new service names

### Environment variables

The full list (verified against `CONTEXT_CHECK.md` §8.5) that needs renaming from `LAUNCHPAD_*` / raw-named to `VECTOR_*`:

| Old | New | Required? | Notes |
|---|---|---|---|
| `APP_API_KEY` | `VECTOR_API_KEY` | Yes | Shared between `vector-api` and `vector-analyzer` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `VECTOR_DB_URL`, `VECTOR_DB_USER`, `VECTOR_DB_PASS` | Yes | Postgres connection |
| `DOCKER_SOCKET` | `VECTOR_DOCKER_SOCKET` | Yes | Docker daemon socket path |
| `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` | `VECTOR_DOCKERHUB_USER`, `VECTOR_DOCKERHUB_TOKEN` | No | For private registries |
| `APP_DEFAULT_PORT` | `VECTOR_APP_DEFAULT_PORT` | No | Default 3000 |
| `ENCRYPTION_KEY` | `VECTOR_ENCRYPTION_KEY` | **Yes — critical** | AES key for env var storage at rest; **rotating this invalidates all stored env vars** |
| `RESEND_API_KEY`, `RESEND_FROM`, `RESEND_TO` | `VECTOR_RESEND_*` | No | Notifications disabled if missing |
| `GITHUB_TOKEN` | `VECTOR_GITHUB_TOKEN` | No | Feature degrades silently if missing |
| `OLLAMA_BASE_URL`, `OLLAMA_MODEL` | `VECTOR_OLLAMA_*` | No | Existing AI integration |
| `DEPLOY_HOOK_SECRET` | `VECTOR_DEPLOY_HOOK_SECRET` | Yes | Webhook HMAC validation |
| `TRAEFIK_NETWORK` | `VECTOR_TRAEFIK_NETWORK` | No | Custom Traefik network name |
| `SESSION_SECRET` | `VECTOR_SESSION_SECRET` | Yes | Frontend session token signing (frontend-only env) |

After Track C (Gemini) adds:
| New | Required? | Notes |
|---|---|---|
| `VECTOR_GEMINI_API_KEY` | Yes (if Gemini is default) | From Google AI Studio free tier |
| `vector.ai.provider=gemini\|ollama` | No | Defaults to gemini |

After Time Machine adds:
| New | Required? | Notes |
|---|---|---|
| `VECTOR_API_INTERNAL_URL` | Yes for analyzer | `http://vector-api:8080` inside Docker network |
| `VECTOR_LOG_RETENTION_DAYS` | No | Default 30 |
| `VECTOR_LOG_RETENTION_DEPLOYS` | No | Default 5 |

**Critical warning on `VECTOR_ENCRYPTION_KEY`:** this key encrypts all stored app env vars at rest. Losing it means all stored env vars are unrecoverable. Rotating it without re-encrypting existing records has the same effect. Back it up separately from the main `.env` and document this in the VPS deployment checklist.

Update `application.properties` accordingly. Keep both names working for a deprecation window if anything in production depends on the old names; otherwise clean break.

### Hetzner VPS concerns

Your VPS has the old Launchpad running. Rename involves:
- New images (can coexist with old, different tags)
- Database is unchanged (same Postgres, same schema) — no migration needed for the rename itself
- Traefik routes to the renamed services — brief downtime during the swap
- Domain stays `filipnikolov.dev`; routes inside the app stay the same URL paths
- Env file on the VPS needs the new variable names

Plan the VPS cutover for a time you won't care about brief downtime.

### Claude Code prompt pattern

Rename work is mechanically tedious. One big prompt per logical chunk:

1. **Prompt 1:** Move folders + rename top-level paths in configs + update imports
2. **Prompt 2:** Introduce parent POM + update module POMs to use it
3. **Prompt 3:** Update env var names across all configs and code
4. **Prompt 4:** Update docker-compose + Dockerfiles
5. **Prompt 5:** Update README + move docs to `docs/planning/`

Commit between each. If prompt 2 breaks the build, you revert just that commit without losing the folder moves.

### Verification

- `docker compose up` brings up everything under new names
- `mvn clean package` at the root builds all modules
- `curl http://localhost/api/apps` works (same endpoint paths, renamed service)
- No references to "launchpad" remain in code/configs (grep: `grep -ri "launchpad" --include="*.{java,ts,tsx,yml,properties,env,md}" .` should only hit historical docs)

---

## 3. Track B — Core refactor

See `REFACTOR_PLAN.md`. Seven phases. Roughly 6–8 focused working days.

Runs after rename. Every file:line reference in `REFACTOR_PLAN.md` assumes original filenames; adjust as you execute — the package names change from `com.filipnikolov.launchpad` to `dev.filipnikolov.vector`, but the class names and line numbers stay the same.

No changes to the refactor plan itself here. Just a dependency note: the rename happens first, the refactor operates on the renamed tree.

---

## 4. Track C — AiProvider abstraction + Gemini migration

**Estimated effort:** 2–3 days
**Prerequisites:** Rename done, refactor Phase 5 done (shared modules exist)

### Goals

1. Introduce `vector-ai` shared module with `AiProvider` interface
2. Implement `GeminiProvider` (primary)
3. Keep `OllamaProvider` as fallback (not required, but nice for "fully self-hosted mode" story)
4. Remove Ollama references from `vector-api` code; Ollama container optional in docker-compose
5. Wire up `vector-api`'s existing AI log analysis endpoint to use `AiProvider`

### Module contents

```
vector-ai/
├── pom.xml
└── src/main/java/dev/filipnikolov/vector/ai/
    ├── AiProvider.java               (interface)
    ├── AiRequest.java                (DTO — prompt, max tokens, etc.)
    ├── AiResponse.java               (DTO — text, provider_used, finish_reason)
    ├── AiProviderException.java
    ├── gemini/
    │   ├── GeminiProvider.java
    │   ├── GeminiClient.java         (HTTP client)
    │   └── GeminiConfig.java         (API key, model name, timeout)
    └── ollama/
        ├── OllamaProvider.java
        └── OllamaConfig.java
```

### Interface

```java
public interface AiProvider {
    AiResponse analyze(AiRequest request) throws AiProviderException;
    String providerName();   // 'gemini-2.5-flash', 'ollama-llama3', etc.
    boolean isAvailable();    // quick health check
}
```

### Selection strategy

Config property `vector.ai.provider=gemini|ollama` picks which Spring bean is wired up. Alternative: both providers registered, orchestrator chooses based on availability — more complex, defer to v2.

For MVP: single provider selected via config. Default to Gemini. If `VECTOR_GEMINI_API_KEY` is missing, fail fast on startup with a clear error (so users know why it's not working).

### Gemini implementation notes

- Use the Google AI REST API, not the Vertex AI one (Vertex requires GCP project setup; public API just needs an API key)
- Endpoint: `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`
- Support streaming responses (needed for Time Machine's AI narration eventually) — but MVP can be non-streaming
- Timeout: 30 seconds total, wrapped in `AiProviderException`
- Rate limit: Gemini free tier is 15 requests per minute. For a portfolio project, plenty.

### Config

Add to `application.properties`:
```properties
vector.ai.provider=gemini
vector.ai.gemini.api-key=${VECTOR_GEMINI_API_KEY}
vector.ai.gemini.model=gemini-2.5-flash
vector.ai.gemini.timeout-seconds=30
```

### Migration of existing Ollama integration

`vector-api`'s current AI log analysis endpoint (`AiController`) uses `OllamaServiceImpl` directly. Changes:

1. `AiController` gains constructor dependency on `AiProvider` (from shared module)
2. `OllamaServiceImpl` removed from `vector-api`; replaced by `OllamaProvider` in `vector-ai` (if keeping Ollama fallback) or dropped entirely
3. Endpoint becomes a thin wrapper calling `aiProvider.analyze(...)`
4. Response type changes from Ollama-specific to `AiResponse`
5. Frontend might need minor tweak if it depended on Ollama-specific response shape

### Docker-compose cleanup

Ollama container becomes optional:

```yaml
# Comment out or remove entirely
# ollama:
#   image: ollama/ollama:latest
#   volumes: ...
```

Frees ~1.5GB RAM on the VPS.

### Decisions

- **D1:** Keep `OllamaProvider` as optional fallback, or remove entirely? Recommend keep — it's 100 lines of code and preserves the "fully self-hosted" story for future. Leave Ollama container commented-out in compose.
- **D2:** Gemini streaming support now or later? Later. MVP is non-streaming. Streaming added when Time Machine Phase 6 needs it.

### Verification

- Existing "Analyze logs" button in the dashboard works, powered by Gemini
- Remove `VECTOR_GEMINI_API_KEY` from env → backend fails to start with clear error
- Latency: analysis responds in ~2-5 seconds (vs. Ollama's 10-30s on CPU)
- Quality: noticeably better explanations compared to Ollama-on-CPU

---

## 5. Track D — Event stream SSE (fix what's been broken)

**Estimated effort:** 2–3 days
**Prerequisites:** refactor Phase 5 (NOTIFY trigger exists)

### Important scope clarification

The context check (`CONTEXT_CHECK.md` §2.4, §2.5, §4.1) revealed that SSE is **already working for container logs** — `RuntimeLogController` streams Docker logs via `SseEmitter(0L)`, consumed by `useBuildLogs.ts` with `EventSource`. This is a valid reference implementation of the pattern.

**What this track needs to build is different:** the missing piece is an **event SSE endpoint** that pushes `deployment_event` rows to the frontend, replacing the 10-second `useEvents` SWR poll. Not a log SSE — that already works.

Where the existing log SSE falls short (and where the new event SSE must do better):
- Log SSE has **no heartbeat**: idle connections drop after ~60s (already flagged in `REFACTOR_PLAN` Finding #7; refactor Phase 1 adds the heartbeat as part of the timeout work)
- No Traefik `flushinterval=100ms` label (`CONTEXT_CHECK.md` §8.3 confirmed no SSE-specific Traefik labels exist) — this affects BOTH the log SSE and the new event SSE; add the label as a shared fix

### Goals

1. Diagnose why previous event-SSE attempts failed (if any — the audit didn't find abandoned attempts, so this may be purely additive)
2. Implement event SSE endpoint (`/api/events/stream`) that fans out `deployment_event` inserts in real time
3. Replace the 10-second SWR poll on the frontend activity feed (`useEvents.ts`)
4. Add Traefik `flushinterval=100ms` label to both log SSE and event SSE paths
5. Make Time Machine's timeline update live via the same event stream

### Reference implementation

Before starting: `frontend/src/hooks/useBuildLogs.ts` is the existing EventSource consumer pattern. Copy its structure for the new `useEventStream` hook. Backend reference: `RuntimeLogController.streamLogs` shows the `SseEmitter` pattern on the server side.

### Diagnosis step (do this FIRST, before any implementation)

Before writing new code, verify there are no abandoned event-SSE attempts in the codebase. The context check found no abandoned code, but it's worth a focused look. Claude Code prompt:

> Search the backend and frontend for any abandoned attempts to build deployment-event SSE (as opposed to log SSE, which works). Look for commented-out controller methods, unused `EventSource` hooks, abandoned `application.properties` keys. Document what you find. If nothing, say so — this track may be purely additive.

Common failure modes for SSE in Spring Boot + Traefik + Next.js setups:

1. **Traefik buffering.** Default response buffering defeats streaming. Fix with service label: `traefik.http.services.<svc>.loadbalancer.responseforwarding.flushinterval=100ms`
2. **Missing heartbeat.** Idle connections drop after ~60s without data. Fix with periodic `:keepalive\n\n` comments (every 15s).
3. **Tomcat connection timeout.** Default 60s idle timeout. Fix with `server.tomcat.connection-timeout=0` or suitable long value.
4. **Spring async not enabled.** Need `@EnableAsync` or `spring.mvc.async.request-timeout=-1`.
5. **Missing headers.** `Cache-Control: no-cache`, `X-Accel-Buffering: no`, `Connection: keep-alive`.
6. **Frontend EventSource not reconnecting correctly.** Needs `onerror` handler with backoff.
7. **Postgres LISTEN connection silently dropping.** Need health check + reconnect.

### Implementation

Backend (`vector-api`):

New endpoint: `GET /api/events/stream`
- Returns `SseEmitter` with long timeout
- On connect, emit an initial "connected" event with current cursor
- Holds a reference to a shared `LISTEN` connection (one for the whole app, not one per client)
- When NOTIFY fires, broadcasts to all connected emitters
- Periodic heartbeat every 15s via `emitter.send(SseEmitter.event().comment("keepalive"))`
- Per-emitter error handling closes the emitter and removes from the set

Architecture choices:
- Single background thread maintains the Postgres LISTEN connection
- Thread-safe set of active emitters
- On NOTIFY, iterate emitters, send event, remove dead ones
- Heartbeat runs on a scheduled executor

Traefik labels for the endpoint:

```yaml
- "traefik.http.services.vector-api.loadbalancer.responseforwarding.flushinterval=100ms"
```

Frontend (`vector-web`):

- `useEventStream()` hook replaces the existing `useSWR` polling for the activity feed
- Uses `EventSource` with auto-reconnect on error (browser default is 3s, which is fine)
- Exposes a React context so multiple components can subscribe without multiple connections
- Falls back to polling if the SSE endpoint returns a non-200 on first connect (old browsers or specific firewall configs)

### Verification

- Open dashboard in two browser tabs, deploy an app in one → second tab shows the deployment event within 1s
- Leave tab open for 10 minutes → connection stays alive, events continue to arrive
- Kill backend → frontend shows "reconnecting..." toast, reconnects automatically on backend restart
- Network throttle in DevTools → graceful degradation to polling
- 50 concurrent EventSource connections → backend stays responsive (test with a load script)

### Decisions

- **D1:** Keep SWR polling as fallback, or commit to SSE entirely? Recommend keep as fallback. SSE works 99% of the time; the 1% fallback prevents a dead dashboard when it fails.
- **D2:** Single shared LISTEN connection vs. per-emitter? Shared. Postgres LISTEN connections are cheap per-connection but the setup overhead isn't worth paying 50× for 50 connected tabs.

---

## 6. Track E — Theme upgrade

See `THEME_UPGRADE.md`. Executes at the end of the major work, after Time Machine is real and stable. The theme upgrade will then style both the original dashboard and the new Time Machine views under the Vector aesthetic.

Reason for doing it last: if you re-theme before Time Machine exists, you'll theme a UI that's about to change significantly. Doing it last means you theme once, against the final surface area.

One mechanical note: `THEME_UPGRADE.md` currently references files under `frontend/src/`. After the rename these live at `vector-web/src/`. The upgrade's grep commands and file paths need updating, but the content/approach is unchanged.

---

## 7. Track F — Flagged future work (not scheduled)

### iOS / macOS companion app

- Plugs in as a consumer of `vector-api`'s REST endpoints + the SSE event stream
- Ties to the iOS course you're planning to take
- SwiftUI, shares code between iOS and macOS
- Scope: read-only dashboard, push notifications for deploys and crashes, QR-code login from web → app

Prerequisites that need to be done first:
- `vector-api` auth must be token-based, not cookie-based (audit Finding #20 — resolve in the refactor)
- API must be versioned (`/api/v1/*`) and stable
- Push notification infrastructure (APNs integration — deferred until app is closer to real)

Not on the roadmap for a specific date. Lives in your head as "when the iOS course happens."

### Fine-tuned ML model for crash analysis

- Plugs into `AiProvider` as a `FineTunedProvider` implementation
- Ties to your ML course
- LoRA fine-tune of a small model (Llama 3.2 1B or similar) on synthetic (log, diff, explanation) triples
- MLX on your M5 for training; convert to GGUF; serve via Ollama locally
- Evaluation against Gemini on held-out examples

Scope: this is its own portfolio project inside the platform. Plan it separately when the ML course starts. The `AiProvider` interface (Track C) guarantees clean integration.

### More microservices as course projects

The platform pattern is deliberately extensible. Future courses may contribute more `vector-*` services:

- Distributed Systems course → semantic log search / tracing service
- Databases course → metrics warehouse / time-series aggregator
- Security course → vulnerability scanner integrated with deployments
- etc.

None are scheduled. Each is its own project when the course starts.

---

## 8. Decisions checklist (across all tracks)

Before starting the overall sequence:

- [ ] **Rename Java package** to `dev.filipnikolov.vector` (recommended) or keep `com.filipnikolov.launchpad`? Recommend the rename — paired with the Vector rename for consistency.
- [ ] **Ollama fallback** — keep or remove? Recommend keep (commented-out container + retained `OllamaProvider` code).
- [ ] **SSE fallback to polling** if SSE fails? Recommend yes.
- [ ] **Push notifications (APNs)** infra built now or deferred? Recommend deferred — not needed until iOS app is close to real.

---

## 9. What this document intentionally doesn't cover

- Details of the core refactor (in `REFACTOR_PLAN.md`)
- Details of the theme upgrade (in `THEME_UPGRADE.md`)
- Details of Time Machine (in `TIME_MACHINE_PLAN.md`)
- Details of the audit findings (in `REVIEW.md`)

This is the index / sequencing / tracks document. Each track has its own depth-document where relevant.

---

## 10. Recommended next action

When you sit down to start executing:

1. Re-read §1 (global sequencing) and confirm you agree with the order
2. Mark decisions in §8
3. Start Track A (rename) first. Weekend afternoon work, one day.
4. Come back to the planning chat for the Claude Code prompt for Track A, chunk 1 (folder moves)
5. Execute, commit, move on

Don't try to start multiple tracks. Finish each before the next.
