# Security Hardening

Security measures implemented on 2026-03-29 as part of [[Security]] hardening before [[VPS Deployment]].

## Completed Items

- Removed `/webhook/test-docker` test endpoint
- Added replay protection (timestamp validation on `/webhook/deploy`)
- Added input validation on `appName` (regex) and `repoUrl` (https:// only)
- Added Spring Security with API key auth on `/api/**` endpoints
- Added rate limiting (30/min webhook, 60/min API per IP)
- Added security headers (HSTS, X-Frame-Options, X-Content-Type-Options)
- Sanitized error responses (no `e.getMessage()` leaked to clients)
- Secured [[Traefik]] dashboard (removed `--api.insecure`, closed port 8080)
- Renamed Docker image to `launchpad-backend`

## Git History

Implemented in commit `4c2fdc7` on branch `feature/security-and-ci`:
> feat: add security hardening, CI/CD pipeline, and Watchtower auto-updates

See also: [[Security]], [[VPS Deployment]], [[Development History]]

#security
