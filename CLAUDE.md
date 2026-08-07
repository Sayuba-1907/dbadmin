# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this app is

DBAdmin lets a user manage a real PostgreSQL database through a UI. The defining property:
**every write happens twice.** Creating a table writes metadata rows (`Tablo`/`Kolon`) *and*
executes a real `CREATE TABLE` against Postgres. The two must never drift apart.

This dual-write shape explains most of the code and most of the surprises — see Architecture below.

## Commands

```bash
# Everything (db + backend + frontend + prometheus + grafana)
docker compose up -d --build
docker compose down          # keeps data (pgdata volume)
docker compose down -v       # wipes the database

# Backend only, after changing backend code or application*.properties
docker compose up -d --build backend
docker compose logs -f backend
```

```bash
cd backend
./mvnw test                                    # all tests (needs Docker: Testcontainers)
./mvnw test -Dtest=TabloServiceIntegrationTest # one class
./mvnw test -Dtest=TabloServiceIntegrationTest#methodName
./mvnw spring-boot:run                         # outside Docker; needs Postgres reachable
```

```bash
cd frontend
npm start            # dev server, :3000
CI=true npm test     # jest; plain `npm test` runs in watch mode
npm run test:e2e     # playwright, frontend/e2e/
npm run format       # prettier (also runs on commit via husky + lint-staged)
```

Ports: frontend 3000, backend 8081, grafana 3001, prometheus 9090, postgres `${DB_PORT}` (5433).
`.env` at the repo root feeds `docker-compose.yml` and is gitignored.

**The jar is built inside the Docker image** (`backend/Dockerfile` runs `mvnw package`), so
`application*.properties` changes need `--build`. A plain restart silently uses the old jar.

## Architecture

### The two write paths

| Path | Goes through | Logger |
|---|---|---|
| Metadata (`tablo`, `kolon`, `sema`, `tag` rows) | Hibernate / Spring Data JPA | `org.hibernate.SQL` |
| Real schema (`CREATE`/`ALTER`/`DROP TABLE`) | `JdbcTemplate` in `ddl/` | `org.springframework.jdbc.core.JdbcTemplate` |

Services call both, in one `@Transactional` method. Consequences worth remembering:

- Enabling `org.hibernate.SQL` logging shows **only the metadata half**. The actual DDL is invisible
  unless the `JdbcTemplate` logger is enabled too.
- `TableDdlExecutor` / `SchemaDdlExecutor` build SQL by **string concatenation** — table and column
  names cannot be JDBC parameters. Safety rests entirely on `validation/NameValidator` plus the
  executors' own `quote()`. Any new DDL must go through both; this is the app's main injection surface.
- Postgres DDL is not transactional in the same sense as the metadata writes. If a DDL statement
  fails after metadata was written, the JPA transaction rolls back the metadata but the DDL that
  already succeeded does not undo itself.

### Naming

All code identifiers (class/entity/DTO names, methods, variables, DB table/column names, REST
paths) are English: `DataTable`, `DataColumn`, `User`, `Notification`, `Schema`, `Tag`. Endpoints
follow the English plural: `/api/tables`, `/api/schemas`, `/api/users`, `/api/notifications`,
`/api/tags`. `DataTable`/`DataColumn` (not `Table`/`Column`) deliberately avoid colliding with the
`jakarta.persistence.Table`/`Column` annotations imported in the same files.

**Comments and UI text stay Turkish.** Javadoc/inline comments explaining *why* code is written a
certain way are not translated — only the symbol names they reference are kept in sync when those
symbols are renamed. `tr.json`/`en.json` translation *values* (and all i18n JSON *keys*, e.g.
`tabloDetail.columnNamePlaceholder`) are untouched — they are product content and lookup strings,
not code identifiers, and changing them was explicitly out of scope for the 2026-08-07 rename (see
DECISIONS.md).

This is a change from the project's earlier convention (Turkish domain terms + English
infrastructure, mixed within a class) — see DECISIONS.md for when and why this was reversed.

### Conventions that are easy to break

- **`Tablo.updatedAt`**: deliberately *not* `@UpdateTimestamp`, because that only fires when the
  `tablo` row itself changes — not when a column is added/renamed/deleted. Every mutating method in
  `TabloService` must call `tablo.touch()` before returning.
- **`@EntityGraph` on `TabloRepository`**: `TabloResponse.from()` touches `kolonlar`,
  `kolonlar.tag` and `schema`, all LAZY. Without the graph, listing 12 tables cost 21 queries.
  New finders that feed a DTO need the same annotation. Only one collection may be fetched this
  way — adding a second `List` relation to the graph throws `MultipleBagFetchException`.
- **`public` is invisible to the API.** It holds the app's *own* metadata tables (`tablo`, `kolon`,
  `sema`, `tag`) plus future extensions, so `SchemaService.isHidden` keeps it out of every listing
  and turns any lookup by its id into a 404 — a `DROP SCHEMA public CASCADE` would delete the
  application itself. There is no `sema` row for it, and `createTablo` therefore requires a
  `schemaId` (it used to default to `public`). New read paths that return `Schema` or `Tablo` must
  go through `isHidden` too.
- **Errors**: thrown as `NotFoundException` / `ConflictException` / `ValidationException`, turned
  into a uniform JSON body (with a machine-readable `code` distinct from the HTTP status) by the
  `@RestControllerAdvice` in `exception/`. Do not return ad-hoc error shapes from controllers.
- **Bean Validation is not on the classpath.** Startup logs
  `NoProviderFoundException ... no Jakarta Validation provider`. `@Valid` / `@NotBlank` would be
  ignored silently; validation is done manually via `NameValidator`. Add
  `spring-boot-starter-validation` before relying on annotations.
- **Metrics**: `MeterRegistry.gauge(...)` re-runs its function on **every** Prometheus scrape (15s,
  see `prometheus.yml`), so a gauge bound to `repository::count` queries the DB continuously even
  when idle. Micrometer also holds the measured object by *weak* reference — it must be kept in a
  field or the metric decays to `NaN`.

### Testing

Integration tests extend `AbstractIntegrationTest`, which starts one shared real Postgres via
Testcontainers using a static initializer (not `@Testcontainers`/`@Container` — that produced a
container per subclass here). No H2, no mocks: the assignment requires a real database. Tests assert
against `information_schema` that the real table/columns exist, not just the metadata rows.

### SQL logging

`backend/src/main/resources/application-dev.properties` holds the Hibernate SQL loggers and a
compact console pattern. It is only read when `SPRING_PROFILES_ACTIVE=dev`, which
`docker-compose.yml` sets by default so production stays quiet. Uncomment the
`org.hibernate.orm.jdbc.bind` line to see bound parameter values (verbose, and it prints every value
in plaintext).

## Working in this repo

- `backend/notlar` holds the supervisor's dated task list (`Yapilacaklar, temmuz 28:`) plus meeting
  notes. Check it before starting or expanding work — some items say who should do them
  (`AI'ya yaptirma`) and many say what comes next.
- `DECISIONS.md` is an ADR-lite journal of what was chosen and what was ruled out. Add to it when
  making a non-obvious call.
- Branches are per week (`frontend/week1`, `backend/week2`) and the assignment expects one PR per
  week, not one per feature.
