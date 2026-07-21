# Decision Journal (ADR-lite)

One line per notable decision: what was chosen, what was ruled out, why.

## Environment / Docker

- **Backend Java version**: set to 21 in `pom.xml`.
  Ruled out: leaving it at 17.
  Why: Dockerfile already builds with JDK 21 and the assignment requires Java 21; the two must match.

- **Frontend container port**: `docker-compose.yml` maps `3000:80`, `Dockerfile` exposes 80.
  Ruled out: `3000:3000` with `EXPOSE 3000`.
  Why: `nginx:alpine` listens on port 80 by default; `EXPOSE` is documentation only and doesn't change that, so the old mapping pointed at a port nothing was listening on.

- **Secrets**: root `.gitignore` added, ignoring `.env`.
  Ruled out: relying on discipline to never `git add .env`.
  Why: `.env` holds the DB password in plain text; a stray `git add .` would have committed it.

- **Testcontainers dependency versions**: `testcontainers`, `junit-jupiter`, `postgresql` all pinned to `1.21.4`.
  Ruled out: the `2.0.5` version Spring Boot 4.1.0's dependency management points at.
  Why: `2.0.5` only exists for the core `testcontainers` module; `junit-jupiter` and `postgresql` submodules haven't published a 2.x release (confirmed against Maven Central's metadata). Mixing 2.0.5 core with 1.21.4 submodules would risk classpath/API mismatches, so all three were forced to the same real version.

- **Docker group membership**: `sudo usermod -aG docker $USER`, verified with `sg docker -c "..."` in the interim.
  Ruled out: running everything through `sudo docker ...` permanently.
  Why: group changes only apply to new login sessions; `sg docker` lets that be confirmed without a full logout/reboot mid-session.

## Domain model / Entities

- **Tablo → Kolon relationship**: `@OneToMany(mappedBy = "tablo", cascade = CascadeType.ALL, orphanRemoval = true)`.
  Ruled out: no cascade, deleting columns manually before deleting a table.
  Why: this relationship is composition per the spec — a column cannot outlive its table.

- **Kolon → Tag relationship**: plain `@ManyToOne`, no cascade.
  Ruled out: cascading delete from Kolon to Tag.
  Why: this relationship is a reference per the spec — a tag is independent and may be shared by other columns.

- **equals()/hashCode() on entities**: id-based equality (`id != null && id.equals(other.id)`), constant `hashCode()` (`getClass().hashCode()`).
  Ruled out: field-based equality (e.g. Lombok `@Data` style, comparing every field).
  Why: Hibernate's lazy-loading proxies make full-field equality unreliable, and a hashCode tied to a field that starts `null` and later gets assigned (the id) breaks entities stored in a `HashSet`/`HashMap` before they're persisted.

- **Kolon.setTablo()**: package-private, not public.
  Ruled out: a public setter symmetric with `setTag()`.
  Why: moving a column between tables should only happen through `Tablo.addKolon()/removeKolon()`, which keeps both sides of the bidirectional relationship in sync; a public setter would let that invariant be broken from outside the entity package.

- **Column type (`Kolon.type`)**: plain `String`, `updatable = false`, whitelist validated in the service layer (not on the entity).
  Ruled out: a JPA `@Enumerated` enum on the entity, or validation inside the entity itself.
  Why: the spec stores the type as plain metadata text; validation is an application-boundary concern (the backend must never trust the client), so it belongs in the service layer, not baked into persistence.

- **Index + uniqueness on `kolon`**: `@Index` on `tablo_id`, composite `@UniqueConstraint` on `(tablo_id, name)`.
  Ruled out: no index (rely on the FK alone), or a plain (global) unique constraint on `name`.
  Why: "list this table's columns" queries filter by `tablo_id`, which Postgres does not index automatically for FKs; and column names only need to be unique within their own table, not across all tables.

## Service layer / DDL

- **Metadata write + real `CREATE/ALTER/DROP TABLE`**: executed inside the same `@Transactional` service method.
  Ruled out: running the DDL in a separate transaction or best-effort after the metadata commit.
  Why: if the DDL fails, the metadata insert must roll back too — otherwise the Tablo/Kolon rows and the real database table can drift out of sync.

- **DDL identifiers (table/column names)**: validated against a whitelist regex, then double-quoted, then concatenated into the SQL string.
  Ruled out: JDBC `?` placeholders for identifiers.
  Why: placeholders only work for values, never for identifiers; the regex + quoting is what keeps hand-built DDL SQL safe from injection.

- **`deleteKolon`**: removes the column via `tablo.removeKolon(kolon)` (triggering `orphanRemoval`), not a direct `kolonRepository.delete(kolon)`.
  Ruled out: deleting the child entity directly through its own repository.
  Why: keeps deletion going through the same parent-owned lifecycle path as every other Tablo/Kolon mutation, consistent with the cascade/orphanRemoval design above.

- **Column input during table creation (`KolonTanimi`)**: a small internal `record`, not a full request DTO.
  Ruled out: building the full DTO/mapper layer before the Controller exists.
  Why: the API-facing DTO layer is a separate, deliberate step (planned right after Controllers); this record only exists to give the service method a typed signature in the meantime.

- **Schema creation**: `spring.jpa.hibernate.ddl-auto=update`.
  Ruled out: Flyway/Liquibase migrations.
  Why: the assignment's scope doesn't call for migration tooling, and Hibernate auto-DDL is enough to create the Tablo/Kolon/Tag metadata tables for this project's size.
