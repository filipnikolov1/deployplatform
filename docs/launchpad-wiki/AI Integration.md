# AI Integration

Local LLM integration via [[Ollama]] for DevOps-focused tasks like log analysis.

## Source

- `AiController.java` in `ai/controller/`
- `OllamaServiceImpl.java` in `ai/service/impl/`
- Additional runtime log access in [[Docker Service]] (`getContainerLogs`)

## How It Works

1. An `ollama` container runs alongside the Launchpad stack in Docker Compose
2. The backend calls `http://ollama:11434/api/generate` (prod) or `http://localhost:11434` (dev from IDE) via a dedicated `RestClient` bean
3. Two endpoints wrap the service for dashboard / CLI use

## One-Time Setup

The `ollama/ollama` image ships empty — models must be pulled once. The `ollama_data` named volume persists the model across restarts, so this is genuinely one-time per volume:

    docker exec -it ollama ollama pull llama3.2:3b

If you forget, the endpoints return **HTTP 503** with `"Model not ready, try again shortly"`.

## Endpoints

Both endpoints sit behind the existing API key filter (see [[Security]]).

### `POST /api/ai/ask`
- **Body:** plain text prompt (≤ 4000 chars)
- **Response:** plain text completion from the model
- **Errors:** 400 on blank/oversize prompt; 503 on Ollama unavailable or model not ready

### `GET /api/ai/logs/analyze?app={name}`
- **Query:** `app` — the application name (must exist in the deployment DB)
- **Response:** plain text 2–3 sentence analysis of the container's recent logs
- **Errors:** 404 on unknown app; 503 on Ollama unavailable or model not ready

The controller fetches the last 200 lines from the running container via [[Docker Service]] `getContainerLogs`, truncates to ~8 KB to fit the model context window, and sends a DevOps-focused prompt to `analyzeLog`.

## Configuration

In `application.properties`:

    launchpad.ai.base-url=${OLLAMA_BASE_URL:http://localhost:11434}
    launchpad.ai.model=${OLLAMA_MODEL:llama3.2:3b}
    launchpad.ai.log-tail-lines=200
    launchpad.ai.request-timeout-seconds=120

The 120 s read timeout is deliberate — cold model loads on a 3B model take 5–30 seconds.

## Error Mapping

| Situation | HTTP | Message |
|-----------|------|---------|
| Ollama down / DNS fail | 503 | `AI service unavailable` |
| Model not pulled yet | 503 | `Model not ready, try again shortly` |
| Read timeout (model mid-load) | 503 | `Model not ready, try again shortly` |
| Ollama 5xx / parse error | 503 | `AI service error` |

## Follow-up Security Item

The compose files currently publish port `11434` to the host. On the VPS this should be removed from `docker-compose.yml` so Ollama is only reachable from within the Docker network (matching how [[PostgreSQL]] is handled). Tracked as a separate item.

See also: [[Architecture Overview]], [[Docker Service]], [[API Reference]], [[Security]]

#feature
