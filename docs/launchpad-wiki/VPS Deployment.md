# VPS Deployment

Remaining items before deploying [[Launchpad]] to a public VPS with a real domain.

## Pre-Deployment Checklist

### Config Changes (deploy day)
- [ ] Rotate all secrets — DockerHub PAT, Resend API key, [[Encryption Service]] key, webhook secret
- [ ] Set a strong `APP_API_KEY` in production `.env`
- [x] ~~Set a strong DB password~~ — already done

### Infrastructure Work
- [ ] **HTTPS via [[Traefik]] + Let's Encrypt** — Add TLS entrypoint + cert resolver to `docker-compose.yml`
- [ ] Update `traefik.domain` from `localhost` to actual domain
- [ ] DNS: point domain + wildcard subdomain (`*.domain.com`) to VPS IP

### Verification
- [ ] Test full [[Deployment Pipeline]] end-to-end on VPS
- [ ] Verify [[Uptime Monitoring]] alerts work with real email
- [ ] Confirm [[Watchtower]] auto-updates work

## Target Architecture on VPS

```
Internet -> DNS -> VPS (port 443)
                     |
                  [ Traefik ] (HTTPS + Let's Encrypt)
                  /    |    \
           Launchpad  App1  App2
                |
            [ Postgres ]
```

All running via `docker compose up -d`.

See also: [[Security]], [[Configuration]], [[Dockerization]]

#infrastructure
