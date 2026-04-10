# Frontend Dashboard

The web UI for managing [[Launchpad]] deployments. A polished dark-themed ops dashboard served by [[Traefik]] as its own Docker container.

## Tech Stack

- Next.js 14+ (App Router, TypeScript)
- Tailwind CSS (custom dark theme, purple accent)
- Custom components (no UI library)
- Dockerized (multi-stage build)

## Architecture

Standalone Next.js app that proxies all API calls to the [[Dashboard API]] backend. The browser never sees the backend URL or API key.

- Served at `appdashboard.{domain}` via [[Traefik]]
- Connects to backend via Docker network
- Auth: password login (env var) + API key proxy

See also: [[Architecture Overview]], [[Security]]

## Pages

1. **Login** — Password-only, minimal centered card
2. **Dashboard** — Icon sidebar + app card grid
3. **App Detail Modal** — Centered popup with info, [[Build Log Streaming|build logs]], and [[Environment Variables|env vars]]

## TODOs

- [ ] Scaffold Next.js project with TypeScript + Tailwind
- [ ] Build login page + auth flow (session cookie)
- [ ] Create API proxy routes (apps, env vars, build logs SSE)
- [ ] Build sidebar component
- [ ] Build app card grid with status dots + auto-refresh
- [ ] Build app detail modal (info, controls, tabs)
- [ ] Build build log viewer (SSE terminal output)
- [ ] Build env var management (view, add, edit, delete)
- [ ] Create Dockerfile (multi-stage build)
- [ ] Add Traefik labels + Docker network config
- [ ] Responsive layout (desktop + tablet)

## Environment Variables

| Variable | Purpose |
|----------|---------|
| `BACKEND_URL` | Backend base URL |
| `API_KEY` | API key for backend auth |
| `DASHBOARD_PASSWORD` | Login password |
| `SESSION_SECRET` | Cookie signing secret |

## Design

- Dark theme: navy backgrounds, purple accents (`#7c3aed`)
- Status colors: green (running), red (failed), yellow (stopped), gray (pending/down)
- Icon sidebar layout
- Centered modal for app details

See [[2026-03-31-frontend-dashboard-design.md]] for full spec.

#feature #frontend
