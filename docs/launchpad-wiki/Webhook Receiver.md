# Webhook Receiver

Receives deploy triggers from GitHub Actions. This is the entry point for the [[Deployment Pipeline]].

## Endpoint

`POST /webhook/deploy` - publicly accessible (no API key needed), protected by [[Webhook Auth]] HMAC signature.

## Source

`WebhookController.java` in `webhook/controller/`

## How It Works

1. Validates HMAC-SHA256 signature via [[Webhook Auth]]
2. Parses JSON payload
3. Validates timestamp (5-minute replay window)
4. Validates `appName` against regex `^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$`
5. Delegates to [[Deployment Service]] to pull and run

## Input Validation

- `appName`: regex-validated, prevents container name injection
- `repoUrl`: must be `https://` (validated at service level)
- `timestamp`: must be within 5 minutes of server time (replay protection)
- `port`: optional, defaults to `APP_DEFAULT_PORT` (3000)

## Security

- HMAC-SHA256 signature verification (see [[Webhook Auth]])
- Replay protection via timestamp validation
- Input sanitization on all fields
- Part of the [[Security]] setup but uses its own auth (not API key)

See also: [[Deployment Pipeline]], [[API Reference]]

#feature #security
