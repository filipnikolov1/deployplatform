# Deployment Service

Orchestrates the deployment lifecycle — creates records, triggers [[Docker Service]], manages status.

## Source

`DeploymentServiceImpl.java` in `deployment/service/impl/`

## Operations

### createDeployment
Called by [[Webhook Receiver]] when a deploy request arrives:
1. Find existing deployment by appName, or create new one
2. Update fields (repoUrl, imageName, containerPort, status=PENDING)
3. Save to [[PostgreSQL]]
4. Fetch [[Environment Variables]] for the app
5. Call [[Docker Service]] `pullAndRun()`
6. Update status to RUNNING or FAILED
7. Save and return

### restartDeployment
Called by [[Dashboard API]]:
1. Load deployment from DB
2. Set status to PENDING
3. Re-pull image and recreate container with latest env vars
4. Update status

### stopDeployment
Called by [[Dashboard API]]:
1. Load deployment from DB
2. Stop and remove container via [[Docker Service]]
3. Set status to STOPPED

### getAllDeployments / getDeployment
Used by [[Dashboard API]] for listing and detail views.

## Deployment Statuses

| Status | Meaning |
|--------|---------|
| PENDING | Deploy/restart in progress |
| RUNNING | Container is up |
| FAILED | Deploy/restart failed |
| STOPPED | Manually stopped via API |
| DOWN | Detected down by [[Uptime Monitoring]] |

Defined in `DeploymentStatus.java` enum.

See also: [[Webhook Receiver]], [[Docker Service]], [[Dashboard API]], [[Database Schema]]

#architecture #feature