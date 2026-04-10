# Security

Security configuration for [[Launchpad]], implemented via Spring Security and custom filters.

## Source

- `SecurityConfig.java` in `config/`
- `ApiKeyAuthFilter.java` in `config/`
- `RateLimitFilter.java` in `config/`

## Layers

### 1. Rate Limiting
`RateLimitFilter` at highest precedence:
- Webhook endpoints (`/webhook/**`): 30 requests/min per IP
- API endpoints (`/api/**`): 60 requests/min per IP
- Returns 429 Too Many Requests when exceeded

### 2. API Key Authentication
`ApiKeyAuthFilter` for `/api/**` endpoints:
- Header: `X-API-Key`
- Compared against `APP_API_KEY` from config
- Build log SSE endpoint is exempt (publicly accessible)

### 3. Webhook Signature Auth
[[Webhook Auth]] for `/webhook/deploy`:
- HMAC-SHA256 signature verification
- Replay protection (5-minute timestamp window)

### 4. Spring Security Config
```
/webhook/**           -> permitAll (has its own HMAC auth)
/api/apps/*/logs/build -> permitAll (SSE build logs)
/api/**               -> authenticated (API key)
everything else       -> denyAll
```

### 5. Security Headers
- `X-Frame-Options: DENY`
- `HSTS: max-age=31536000; includeSubDomains`
- `X-Content-Type-Options: nosniff` (Spring Security default)
- CSRF disabled (stateless API)

### 6. Input Validation
- appName: regex `^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$`
- Request body limits: 1MB max
- Error responses sanitized (no stack traces or internal messages)

### 7. Traefik Dashboard
Secured — `--api.insecure` removed, port 8080 not exposed.

## Completed Security Hardening (2026-03-29)

See [[Security Hardening]] for the full list of items addressed.

## Remaining TODOs

See [[VPS Deployment]] for pre-production security items.

#security #architecture