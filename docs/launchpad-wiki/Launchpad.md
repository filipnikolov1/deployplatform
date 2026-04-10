# Launchpad

A self-hosted PaaS platform (mini Heroku/Vercel) built with [[Spring Boot]] and [[Docker]]. Designed as a portfolio showcase for infrastructure, automation, and full-stack skills.

## What it does

Launchpad receives deploy requests via [[Webhook Receiver]], pulls Docker images from [[DockerHub Integration]], and hosts them as containers routed through [[Traefik]] at `appname.domain.com`.

## Core Pipeline

```
Push code -> GitHub Actions -> DockerHub -> Launchpad webhook -> Pull & deploy -> Traefik routing
```

See [[Deployment Pipeline]] for the full flow.

## Features

1. [[Webhook Receiver]] - Receives deploy triggers from GitHub Actions
2. [[DockerHub Integration]] - Authenticated image pulls
3. [[Environment Variables]] - AES-256-GCM encrypted env var management
4. [[Build Log Streaming]] - Real-time SSE log streaming
5. [[Uptime Monitoring]] - Health checks with email alerts
6. [[Dashboard API]] - REST API for managing deployments
7. [[Dockerization]] - Multi-stage build, docker-compose with all services

## Architecture

- [[Architecture Overview]] - System design and component relationships
- [[Database Schema]] - PostgreSQL tables and migrations
- [[API Reference]] - All HTTP endpoints
- [[Security]] - Auth, rate limiting, input validation

## Infrastructure

- [[Docker]] - Container management via docker-java
- [[Traefik]] - Reverse proxy and subdomain routing
- [[PostgreSQL]] - Persistent storage with Flyway migrations
- [[CI/CD Pipeline]] - GitHub Actions build and push
- [[Watchtower]] - Auto-updates for Launchpad itself

## Status

All 7 features complete. End-to-end pipeline tested 2026-03-28. See [[Project Roadmap]] for what's next.

#architecture
