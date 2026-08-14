# Decision Journal (ADR-lite)

One line per notable decision: what was chosen, what was ruled out, why. Kept to
decisions that actually shaped the architecture or would come up in a design
review/interview — routine setup steps (pinning a dependency version, fixing a
port mapping, adding a `.gitignore` line) are not recorded here.

## Core architecture: the dual write

- **Metadata write + real `CREATE/ALTER/DROP TABLE`**: executed inside the same
  `@Transactional` service method.
  Ruled out: running the DDL in a separate transaction or best-effort after the
  metadata commit.
  Why: if the DDL fails, the metadata insert must roll back too — otherwise the
  `DataTable`/`DataColumn` rows and the real database table can drift out of sync.
  This is the single property the whole app is built around.

- **DDL identifiers (table/column names)**: validated against a whitelist regex,
  then double-quoted, then concatenated into the SQL string.
  Ruled out: JDBC `?` placeholders for identifiers.
  Why: placeholders only work for values, never for identifiers; the regex +
  quoting is what keeps hand-built DDL SQL safe from injection. Every new DDL
  code path has to go through both, or the app's main injection surface reopens.

- **Real composite `PRIMARY KEY`, no surrogate `id` column**: PK-flagged columns
  get a genuine `CONSTRAINT "<table>_pkey" PRIMARY KEY (...)`; the auto `id`
  column was removed entirely.
  Ruled out: keeping a cosmetic `UNIQUE` constraint (previous behavior) or a
  hidden surrogate PK alongside a "visual" one.
  Why: `UNIQUE` isn't `PRIMARY KEY` — it accepts NULLs, and Postgres treats
  multiple NULL rows as distinct. The output had to look identical to a
  hand-written `CREATE TABLE ... PRIMARY KEY (...)`, and a table can only have
  one real PK, so the fake surrogate had to go. Known edge case: marking an
  existing column PK on a table that already has NULLs in it is rejected by
  Postgres and rolls back cleanly.

## Domain model

- **`DataTable` → `DataColumn`**: `@OneToMany(cascade = ALL, orphanRemoval = true)`.
  **`DataColumn` → `Tag`**: plain `@ManyToOne`, no cascade.
  Why: the first relationship is composition (a column cannot outlive its
  table); the second is a reference (a tag is independent, shared across columns).

- **`equals()`/`hashCode()` on entities**: id-based equality, constant
  `hashCode()`.
  Ruled out: field-based equality (Lombok `@Data` style).
  Why: Hibernate's lazy-loading proxies make full-field equality unreliable, and
  a `hashCode()` tied to a field that starts `null` (the id) breaks entities
  stored in a `HashSet`/`HashMap` before they're persisted — a classic JPA
  gotcha worth knowing cold in a review.

- **Index on `columns.table_id`**: B-tree index added after measuring a real
  N+1/full-scan cost.
  Why: "list this table's columns" filters by `table_id`, which Postgres does
  not index automatically for a foreign key. Measured impact at 500k rows:
  `Seq Scan` at ~21ms → `Bitmap Index Scan` at ~0.2ms.

- **`TabloResponse`/DTOs instead of returning JPA entities from controllers**.
  Why: `DataTable`/`DataColumn` reference each other; serializing the entity
  graph directly recurses forever. DTOs point one way only.

## Testing strategy

- **One shared `static` Postgres container** (`AbstractIntegrationTest`), no
  mocks, no H2 — real Postgres for every integration test.
  Ruled out (found the hard way): `@Testcontainers`/`@Container` on the field,
  which looked shared but silently started a new container per test class —
  full suite time went from ~7s to ~2m15s before this was caught and fixed with
  a plain `static { POSTGRES.start(); }` block.
  Why: this project's whole premise is that metadata and the real database
  object stay in sync — a mocked DB couldn't test that claim at all.

- **Assertions query `information_schema` directly** (via `JdbcTemplate`), not
  just the JPA repositories.
  Why: a test that only checks the metadata row would still pass if the real
  `CREATE TABLE` silently failed — this is the test-side enforcement of the
  dual-write guarantee above.

- **E2E: Playwright, driving the real Docker frontend + real backend + real DB.**
  Why chosen: unlike component tests, it catches things only a real browser
  surfaces — a stale CSS selector after a rename, a form that overflows its
  container and becomes unclickable while still looking fine in a screenshot.
  Critical incident/lesson: a coordinate-based click on a native `<select>`
  (role dropdown) during an automated click-through session landed on the wrong
  row and silently demoted the real `admin` account to VIEWER — native selects
  render outside the page DOM, so pixel coordinates aren't reliable. Fixed
  by hand (DB + Redis cache), and the rule going forward is: never
  coordinate-click a native `<select>`, always focus + keyboard, and always
  take a fresh screenshot immediately before each click if the layout could
  have shifted.

## API design

- **Two endpoints for column name vs. tag** (`PATCH /columns/{id}/name`,
  `PATCH /columns/{id}/tag`) instead of one combined partial-update endpoint.
  Why: a Java record can't distinguish "field not sent" from "field sent as
  null", and `tagId == null` is meaningful (clear the tag). Two endpoints
  sidestep the ambiguity instead of adding a wrapper type.

- **Centralized error handling**: one `@RestControllerAdvice` maps
  `ValidationException`/`NotFoundException`/`ConflictException` to 400/404/409
  with a shared body (`status`, machine-readable `code`, `details`), and Spring's
  own failures (bad JSON, wrong HTTP method, unknown path, DB constraint
  violations) are mapped the same way instead of leaking Spring's default error
  shape or a raw stack trace to the client.
  Why: every operation must fail the same shape no matter what broke, and raw
  Postgres/Jackson error text can contain internal identifiers that shouldn't
  reach a client.

## `public` schema is invisible to the API

- **Never listed, 404 on lookup by id, cannot be created or targeted.**
  Why: `public` holds the app's own metadata tables (`tables`, `columns`,
  `schemas`, `tag`); if it were a normal schema, a user could create a table
  into it (making an app table look like user data) or, worse, a
  `DROP SCHEMA public CASCADE` would delete the application itself. The guard
  (`SchemaService.isHidden`) is intentionally cheap (a string comparison) for
  something whose failure mode is catastrophic.

## Authentication & authorization

- **Users live in the database, not `application.properties`.**
  Why: roles/users need to change at runtime; a properties-file list would need
  a rebuild+restart per change and would commit passwords to the repo.

- **Three roles: VIEWER / EDITOR / ADMIN** (not just the originally-specified
  two).
  Why: with only VIEWER/EDITOR, nobody could manage users — ADMIN fills that
  gap and makes "different roles for different users" actually usable
  end-to-end.

- **JWT via `jjwt`, hand-written `JwtService`/`JwtAuthenticationFilter`**,
  not `spring-boot-starter-oauth2-resource-server`.
  Why: this is a learning project — the goal was for the signing/verification
  mechanism to be visible and readable (~100 lines), not hidden behind a
  resource-server black box.

- **`/actuator/prometheus` and `/actuator/health` left unauthenticated.**
  Why: Prometheus scrapes them every 15s with no credentials; protecting them
  would make Grafana dashboards go quietly empty (no error, just no data) —
  a failure mode intentionally covered by its own security test.

- **Last remaining ADMIN can't be deleted or demoted.**
  Why: without this, a single admin could lock everyone (including themselves)
  out of user management with no recovery path short of a manual DB edit.

## Redis — why it's here at all

- **Manual `RedisTemplate`, not `@Cacheable`/`@CacheEvict`.**
  Why: the point of adding Redis (per the mentor's notes) was to *learn* it —
  annotation-based caching hides every step (get/put/evict, TTL, when a miss
  falls through to the DB). `KullaniciRolCacheService` makes all of that
  explicit in code.
  What it's used for: caching the requesting user's role in
  `JwtAuthenticationFilter`, so most requests skip a DB round-trip.
  Honest caveat, stated in the notes themselves: at this project's scale, Redis
  is arguably overengineering — it was added to learn the technology, not
  because the load demanded it.

- **Fail-open**: every cache method swallows Redis errors and logs them,
  never lets one propagate into a 500.
  Why: a cache is an optimization, not a dependency — the app should keep
  working (a bit slower) if the Redis container is down. The Lettuce timeout
  was also dropped from its 60s default to 300ms, or fail-open would be
  theoretical rather than real: a live test showed requests taking ~60s with
  Redis down vs. ~0.6s after the timeout fix.

- **`TracingAwareRedisCacheWriter`**: a hand-written `RedisCacheWriter` replacing
  Spring's default, found necessary by direct observation in Tempo.
  Why: `DefaultRedisCacheWriter` grabs its connection a different way than
  `RedisTemplate` does, and only the `RedisTemplate` path was getting correctly
  parented to the active trace — `@Cacheable`-driven Redis commands (in
  Tag/User/Schema services) were showing up in Tempo as broken, parentless
  spans. This is a real bug found via tracing, not a hypothetical.

## RabbitMQ — why it's here at all

- **Notification delivery moved off in-process `ApplicationEventPublisher`
  onto a durable RabbitMQ queue**, with retry + a Dead Letter Queue.
  Why added: explicitly to learn a message broker and the fan-out/fan-in
  pattern (per the mentor's notes) — the in-process version worked but lost
  every pending notification on a restart, and had no visibility into failed
  delivery.
  Why it's correct here: publish only happens on transaction commit (a
  rollback publishes nothing), and a message that can't be processed after
  retries lands in the DLQ instead of vanishing silently.

## Observability: OpenTelemetry, and why no Collector

- **Backend exports traces to Tempo and logs to Loki directly**, no OTel
  Collector in between.
  Why: a Collector's real value (backend-agnostic routing, swappable trace/log
  destinations) doesn't pay for itself with exactly one trace backend and one
  log backend that aren't expected to change — it would just be another
  container and another config file with no corresponding benefit here.

- **Structured logs go to Loki (not just stdout/a file), correlated with
  traces via a shared trace/span id.**
  Why: the value isn't logging in isolation — it's being able to jump from a
  single log line straight to the full trace it happened inside of (which DB
  call, which Redis call, how long each took), instead of grepping timestamps
  across two separate tools by hand.

## Audit log & backup

- **`applyChanges` writes one summary audit row for a whole save, not one row
  per sub-operation** (rename + add column + tag change all bundled).
  Why: explicit user requirement — a single "Save" click producing N audit rows
  would make the log unreadable. Every granular method got split into an
  audit-free `...Core` variant plus a public audit-writing wrapper;
  `applyChanges` calls the Core variants and writes exactly one row at the end.

- **Audit log backups go to MinIO (S3-compatible object storage) as JSON, not
  a database archive table**, and a failed upload leaves the database rows
  untouched (fail-closed) — verified with a real MinIO Testcontainer, not a
  mock, to keep the "real dependencies in tests" rule intact even here.
  Why: a growing audit table needed periodic offloading without losing
  history or risking silent data loss if the upload itself fails.

## Internationalization

- **Two separate i18n systems** — `react-i18next` on the frontend, Spring
  `MessageSource` + `Accept-Language` on the backend — instead of one shared
  source of truth.
  Why: they serve different audiences. The frontend needs a language switch to
  update on-screen text instantly with no server round-trip; the backend's
  translated messages exist for people who never touch the frontend at all
  (Swagger, Postman, curl, logs). Backend errors carry a machine-readable
  `code` + a `details` map — the frontend translates `code` itself rather than
  displaying the backend's message text, so the two systems never fight over
  which string wins.

## Rename to English (2026-08-07)

- **Full codebase rename** (`Tablo`→`DataTable`, `Kolon`→`DataColumn`,
  `Kullanici`→`User`, `/api/tablolar`→`/api/tables`, etc.), migrated
  data-preserving (`ALTER TABLE/COLUMN RENAME` against the live DB, not a
  reset), comments and UI text left in Turkish on purpose.
  Why: explicit user decision reversing an earlier mixed-language convention.
  Real lesson from doing it: a word-bounded bulk regex rename doesn't
  distinguish code identifiers from JSON body field names, template-literal
  interpolations, or hardcoded test-assertion strings — two real bugs shipped
  this way (a login field silently renamed on one side only; a URL template
  losing its `${id}` interpolation) and were only caught by a manual
  API-body-vs-DTO diff and a real login attempt, not by `tsc --noEmit` or
  green tests alone.
