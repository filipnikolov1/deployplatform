# Uptime Monitoring

Scheduled health checks for all deployed apps, with email alerts when an app goes down.

## Source

- `UptimeMonitorServiceImpl.java` in `monitoring/service/impl/`
- `NotificationServiceImpl.java` in `monitoring/service/impl/`

## How It Works

Every **60 seconds** (`@Scheduled(fixedRate = 60000)`):

1. Query all RUNNING deployments from [[PostgreSQL]]
2. For each, check `dockerService.isContainerRunning(appName)` via [[Docker Service]]
3. If container is NOT running:
   - Mark deployment as `DOWN`
   - Send email alert via [[Notification Service]]
4. Also check all DOWN deployments — if container recovered, mark as `RUNNING`

## Health Check Method

Uses Docker API `inspectContainerCmd` to check if the container's state is `running`. This is a process-level check (is the container up?), not an HTTP health check.

## Email Alerts

Powered by the [[Notification Service]] using Resend API.

## Deployment Statuses Affected

| From | To | Trigger |
|------|----|---------|
| RUNNING | DOWN | Container not running |
| DOWN | RUNNING | Container recovered |

See also: [[Deployment Service]], [[Notification Service]], [[Docker Service]]

#feature