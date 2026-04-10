# Notification Service

Sends email alerts when apps go down. Used by [[Uptime Monitoring]].

## Source

`NotificationServiceImpl.java` in `monitoring/service/impl/`

## Provider

Uses **Resend API** for email delivery.

## Configuration

```properties
resend.api-key=${RESEND_API_KEY:}
resend.from=${RESEND_FROM:Launchpad <onboarding@resend.dev>}
resend.to=${RESEND_TO:}
```

Set via `.env` file — see [[Configuration]].

## Behavior

- `sendDownAlert(appName)` — sends an email notification when a container is detected as down
- Only triggers when status transitions from RUNNING to DOWN (not on every check)

See also: [[Uptime Monitoring]], [[Configuration]]

#feature