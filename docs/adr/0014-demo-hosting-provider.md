# ADR-0014: Demo hosting provider

- **Status:** Proposed
- **Date:** 2026-11-08

## Context

The `demo` environment ([10](../10-devops.md) §6) is used for sprint reviews, the final presentation and teacher access. It is deployed from `main` by `deploy.yml` when a `v*` tag is pushed: Docker images are built, pushed to GitHub Container Registry, deployed to the hosting, and checked with a smoke test on `/actuator/health`.

The stack ([ADR-0013](0013-technology-stack-and-versions.md)) is a Spring Boot 4 backend (Java 21), PostgreSQL 18 and an Angular frontend served by nginx, which also proxies `/api` to the backend. The whole stack already runs from one `docker-compose.yml`.

Criteria from [10](../10-devops.md):

- free or student tier
- Docker support
- managed PostgreSQL or a persistent volume
- HTTPS
- does not sleep during the presentation

The demo contains only seed data, no real customer money or personal data.

## Decision

We run the demo on **one small VPS with Docker Compose**.

- `deploy.yml` connects to the VPS over SSH, pulls the tagged images from GHCR and runs `docker compose up -d` with a production compose file.
- PostgreSQL runs as a container with a persistent volume on the VPS.
- A reverse proxy (Caddy) in front of the frontend gives HTTPS with an automatic certificate; for this the demo needs a domain name (a free subdomain is enough).
- Secrets (`DB_PASSWORD`, `JWT_SECRET`, `ADMIN_PASSWORD`, SSH key) are stored in the GitHub Environment `demo`, never in the repository.
- The VPS provider is chosen by the DevOps member by price at the time of setup (a student offer is preferred). The provider is not part of this decision; only the VPS + Compose approach is.

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| VPS with Docker Compose | Same stack as local, no cold start, full control, predictable cost | We set up and secure the server ourselves (SSH, firewall, updates, backups) |
| Render | Easy GitHub deploy, managed PostgreSQL | Free tier can sleep and has database limits; paid plan needed to avoid it |
| Railway | Simple setup, managed PostgreSQL | Usage-based billing, limited free credit |
| Fly.io | Docker-first | More CLI configuration; database setup is more manual |
| Azure / AWS student credits | Powerful, managed services | Complex for the project size, credits can run out, risk of unexpected cost |

Free-tier limits and prices change often; they must be checked on the provider's site at setup time.

## Consequences

- The demo does not sleep, so it is safe to show during the presentation.
- The deploy job is the same as the local setup, so what works in `docker compose up` works in the demo.
- The team is responsible for server security: SSH keys only, firewall with ports 80/443 open, regular updates.
- The database volume is not backed up automatically; the demo data can be recreated from the seed.
- Moving to a PaaS later is cheap, because the images are already built and stored in GHCR. A move needs a new ADR that supersedes this one.
