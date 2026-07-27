# DBAdmin

A small three-tier web application that lets a user manage a database through a UI —
create/rename/delete tables, add/remove columns, tag columns — without writing SQL by hand.

Every write (create table, add column, ...) does two things at once: it writes metadata rows
(`Tablo`/`Kolon`/`Tag`) **and** runs the real `CREATE`/`ALTER`/`DROP TABLE` statement, so the
metadata and the actual database schema always stay in sync.

## Stack

| Layer      | Tech                                              |
|------------|----------------------------------------------------|
| Database   | PostgreSQL 15                                      |
| Backend    | Spring Boot, Java 21, Spring Data JPA, Maven       |
| Frontend   | React + TypeScript                                 |
| Testing    | JUnit, Testcontainers (real Postgres, no mocks/H2) |
| Containers | Docker, Docker Compose                             |

## Prerequisites

- Docker & Docker Compose
- (Only if running services outside Docker) Java 21 + Maven, Node.js for the frontend

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/Sayuba-1907/dbadmin.git
   cd dbadmin
   ```

2. Create a `.env` file in the project root (used by `docker-compose.yml`):
   ```env
   POSTGRES_DB=dbadmin_db
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=your_password_here
   DB_PORT=5433
   ```
   `.env` is gitignored — it holds the DB password, so it is never committed.

3. Start everything:
   ```bash
   docker compose up -d --build
   ```

4. Open the app:
   - Frontend: http://localhost:3000
   - Backend API: http://localhost:8080/api
   - Postgres: `localhost:${DB_PORT}` (default `5433`, mapped so it doesn't collide with a local Postgres install on 5432)

5. Stop everything:
   ```bash
   docker compose down       # keeps data (named volume `pgdata`)
   docker compose down -v    # also wipes the database
   ```

## Running without Docker (local development)

- **Backend**: needs a Postgres instance reachable at the URL/credentials in
  `backend/src/main/resources/application.properties` (or via `SPRING_DATASOURCE_*` env vars),
  then from `backend/`: `./mvnw spring-boot:run`
- **Frontend**: from `frontend/`: `npm install && npm start` (dev server on http://localhost:3000,
  proxies API calls to the backend)

## Running tests

```bash
cd backend
./mvnw test
```

Integration tests spin up a real PostgreSQL container via Testcontainers (Docker must be running).
Unit tests cover validation logic and column-type mapping; integration tests cover the
controller/service/DDL flow end to end, including asserting against `information_schema` that the
real table/columns were actually created — not just the metadata rows.

## API overview

All endpoints are under `/api`. Responses are DTOs, not JPA entities.

**Tables** (`/api/tablolar`)
| Method | Path                                  | Description                          |
|--------|----------------------------------------|--------------------------------------|
| GET    | `/api/tablolar`                        | List all tables                      |
| GET    | `/api/tablolar/{id}`                   | Get one table with its columns       |
| POST   | `/api/tablolar`                        | Create a table (name + columns)      |
| PATCH  | `/api/tablolar/{id}`                   | Rename a table                       |
| DELETE | `/api/tablolar/{id}`                   | Delete a table (cascades to columns) |
| POST   | `/api/tablolar/{id}/kolonlar`          | Add a column to a table              |
| DELETE | `/api/tablolar/{id}/kolonlar/{kolonId}`| Delete a column                      |
| PATCH  | `/api/tablolar/{id}/kolonlar/{kolonId}/name` | Rename a column                |
| PATCH  | `/api/tablolar/{id}/kolonlar/{kolonId}/tag`  | Change/clear a column's tag    |

**Tags** (`/api/tags`)
| Method | Path         | Description       |
|--------|--------------|--------------------|
| GET    | `/api/tags`  | List all tags      |
| POST   | `/api/tags`  | Create a tag       |

### Validation rules

Applied to both table and column names, enforced on the backend regardless of what the frontend
sends:
- Length: 2–30 characters
- Must not start with an uppercase letter
- Letters, digits and underscore only
- Duplicate name (within the relevant scope) → `409 Conflict`, not `400`

Column `type` must be one of `numeric` / `text` / `datetime` / `boolean` (`datetime` maps to
Postgres `timestamp`); it is fixed at creation and cannot be changed afterwards.

Validation failures and conflicts return a consistent JSON error body (status, error, message,
machine-readable `code`, and a `details` map) via a centralized `@RestControllerAdvice`. The
frontend shows these as colour-coded notifications (green/amber/red by HTTP status).

## Project structure

```
backend/    Spring Boot app (entity / repository / service / controller / dto / ddl / validation)
frontend/   React + TypeScript app (dashboard, table sidebar, column detail, i18n TR/EN)
DECISIONS.md   ADR-lite decision journal — what was chosen, what was ruled out, and why
docker-compose.yml   db + backend + frontend, one container each
```

See [`DECISIONS.md`](./DECISIONS.md) for the reasoning behind notable design choices.
