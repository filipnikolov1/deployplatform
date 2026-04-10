# Webhook Auth

HMAC-SHA256 signature verification for the [[Webhook Receiver]]. Ensures deploy requests are authentic.

## Source

`WebhookAuthServiceImpl.java` in `webhook/auth/service/impl/`

## How It Works

1. Caller computes `HMAC-SHA256(payload, secret)` and sends as `X-Signature-256: sha256=<hex>`
2. Launchpad recomputes the HMAC using `GITHUB_WEBHOOK_SECRET` from config
3. Compares using `MessageDigest.isEqual()` (constant-time comparison to prevent timing attacks)

## Configuration

- `github.webhook.secret` property, set via `GITHUB_WEBHOOK_SECRET` env var
- Same secret must be configured in the calling GitHub Actions workflow

## Security Notes

- Constant-time comparison prevents timing side-channel attacks
- Signature prefix `sha256=` is validated before comparison
- Returns `false` (not exception) on any failure — no information leakage

See also: [[Webhook Receiver]], [[Security]], [[Deployment Pipeline]]

#security
