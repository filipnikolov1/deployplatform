# Architecture Overview

[[Launchpad]] is a [[Spring Boot]] backend that orchestrates [[Docker]] containers, stores state in [[PostgreSQL]], and uses [[Traefik]] for routing.

## Component Diagram

```
                    Internet
                       |
                   [ Traefik ]
                   /    |    \
            Launchpad  App1  App2  ...
                |
            [ Postgres ]
```

## Package Structure

```
com.filipnikolov.launchpad
├── config/              -> [[Security]], rate limiting
├── deployment/          -> [[Dashboard API]], [[Deployment Service]]
│   ├── controller/      -> DeploymentController
│   ├── model/           -> Deployment, DeploymentStatus
│   ├── repository/      -> DeploymentRepository
│   └── service/         -> DeploymentService + impl
├── docker/
│   ├── buildlog/        -> [[Build Log Streaming]]
│   │   ├── controller/  -> BuildLogController (SSE)
│   │   └── service/     -> BuildLogService + impl
│   └── service/         -> [[Docker Service]]
├── envvar/              -> [[Environment Variables]]
│   ├── controller/      -> EnvVarController
│   ├── crypto/          -> [[Encryption Service]]
│   ├── model/           -> EnvVar entity
│   ├── repository/      -> EnvVarRepository
│   └── service/         -> EnvVarService + impl
├── exception/           -> GlobalExceptionHandler
├── monitoring/          -> [[Uptime Monitoring]]
│   └── service/         -> UptimeMonitorService, NotificationService
└── webhook/             -> [[Webhook Receiver]]
    ├── auth/            -> [[Webhook Auth]]
    └── controller/      -> WebhookController
```

## Request Flow

1. HTTP request hits [[Traefik]] on port 80
2. Traefik routes to [[Launchpad]] (port 8082) or to a deployed app
3. [[Security]] filter chain: rate limit -> API key auth (for `/api/**`)
4. Controller handles request, delegates to service layer
5. Service layer interacts with [[Docker Service]] and [[PostgreSQL]]

## Key Design Decisions

- **Stateless sessions** - No server-side sessions, API key auth only
- **Docker socket mounting** - Direct Docker API access via docker-java
- **Traefik labels** - Each deployed container gets Traefik labels for automatic routing
- **Flyway migrations** - Database schema versioned and auto-applied
- **SSE for logs** - Server-Sent Events for real-time build log streaming

See also: [[Database Schema]], [[API Reference]], [[Security]]

#architecture
