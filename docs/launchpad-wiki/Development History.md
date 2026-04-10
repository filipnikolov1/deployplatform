# Development History

Git history of [[Launchpad]], showing the feature-by-feature build progression.

## Timeline (newest first)

| Commit | Branch | Description |
|--------|--------|-------------|
| `4c2fdc7` | feature/security-and-ci | [[Security Hardening]], [[CI/CD Pipeline]], [[Watchtower]] |
| `9aa9961` | feature/dockerize | [[Dockerization]] — Dockerfile, docker-compose, full containerization |
| `850badc` | feature/dashboard-api | [[Dashboard API]] — REST endpoints for stop/restart |
| `21ded09` | feature/uptime-monitoring | [[Uptime Monitoring]] — health checks + email alerts |
| `a2c8209` | feature/live-log-streaming | [[Build Log Streaming]] — SSE real-time logs |
| `f9ef588` | feature/env-vars-manager | [[Environment Variables]] — encrypted env var CRUD |
| `3f2f2d0` | feature/dockerhub-auth | [[DockerHub Integration]] — authenticated pulls |
| `df73897` | feature/traefik-integration | [[Traefik]] subdomain routing |
| `805cc51` | main (initial) | Initial setup — webhook receiver + Docker integration |

## Branch Strategy

Feature branches merged via PRs to `main`. Each feature = 1 branch = 1 PR.

| PR | Feature |
|----|---------|
| #1 | Traefik integration |
| #2 | DockerHub auth |
| #3 | Env vars manager |
| #4 | Live log streaming |
| #5 | Uptime monitoring |
| #6 | Dashboard API |
| #7 | Dockerize |

## Current Branch

`feature/security-and-ci` — security hardening + CI/CD + Watchtower. Not yet merged to main.

See also: [[Project Roadmap]]

#architecture