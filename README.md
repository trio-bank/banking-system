# Trio Bank

Educational simulation of a Polish retail bank — accounts, transfers, ATM, interest and an admin panel — built with Java / Spring Boot, PostgreSQL and Angular.

> ⚠️ Educational project. No real money, no real personal data.

**Team:** Marko Zhenchuk · Hryhorii Martynkian · Kateryna Valentyuk

## Documentation

Everything about the project — requirements, data model, API contract, architecture, process and roadmap — is in [`/docs`](docs/README.md).

## Running locally

```bash
cp .env.example .env
docker compose up --build
```

- Frontend: http://localhost:4200
- Backend API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html

Details: [docs/10-devops.md](docs/10-devops.md#8-local-development).

## Backend: how to run

Requires JDK 21 and a running Docker daemon.

```bash
docker compose up db
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Or run `TrioBankApplication` from the IDE with the `local` profile.

If a local PostgreSQL already uses port 5432, the app connects to it instead of the container and fails with `password authentication failed`. Pick another host port:

```bash
DB_PORT=15432 docker compose up db
DB_URL=jdbc:postgresql://localhost:15432/triobank ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- Health: http://localhost:8080/actuator/health
- Swagger UI (`local` and `demo` profiles only): http://localhost:8080/swagger-ui.html

Build and run all tests (Testcontainers starts its own `postgres:18`):

```bash
cd backend
./mvnw verify
```
## Frontend: how to run

```bash
cd frontend
npm ci
npm start         # http://localhost:4200, /api is proxied to the backend on :8080
```

Requirements, all commands and details: [frontend/README.md](frontend/README.md).

## Repository layout

| Folder | Content |
|---|---|
| `backend/` | Spring Boot API |
| `frontend/` | Angular application |
| `e2e/` | Playwright tests |
| `docs/` | Project documentation |
