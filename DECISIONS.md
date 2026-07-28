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

- **Volume persistence, verified empirically**: created a table, ran `docker compose down` (no `-v`) + `up` — table survived even though every container was destroyed and recreated. Then ran `docker compose down -v` + `up` — table was gone.
  Ruled out: trusting the `pgdata:/var/lib/postgresql/data` volume mapping in `docker-compose.yml` without proving it.
  Why: containers are ephemeral by design; the `-v` vs. no-`-v` contrast is the actual proof that data survival comes from the named volume, not from the container itself.

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

## Controller / DTO / API

- **Response shape**: dedicated DTOs (`TabloResponse`/`KolonResponse`) instead of returning entities from the controller.
  Ruled out: returning `Tablo`/`Kolon` entities directly.
  Why: `Tablo` and `Kolon` reference each other (`Tablo.kolonlar` and `Kolon.tablo`); serializing an entity graph like that recurses forever (`Tablo` → its `kolonlar` → each `Kolon`'s `tablo` → its `kolonlar` → ...). The response DTOs only point one way, so the cycle can't happen.

- **Changing a column's name vs. its tag**: two separate endpoints (`PATCH /kolonlar/{id}/name`, `PATCH /kolonlar/{id}/tag`) instead of one combined partial-update endpoint.
  Ruled out: a single `PATCH /kolonlar/{id}` accepting optional `name`/`tagId` fields.
  Why: a Java record can't tell "field not sent" apart from "field sent as null" without an extra wrapper type, and `tagId == null` is a meaningful value here (clear the tag). A combined endpoint would make "clear the tag" and "don't touch the tag" indistinguishable; two endpoints sidesteps the ambiguity entirely.

- **Error handling**: centralized in one `@RestControllerAdvice` (`GlobalExceptionHandler`) mapping `ValidationException`/`NotFoundException`/`ConflictException` to 400/404/409 with a shared `ErrorResponse` body.
  Ruled out: `try`/`catch` in each controller method.
  Why: keeps every controller method free of error-handling boilerplate and guarantees the frontend always gets the same error shape back, no matter which operation failed.

- **Tag CRUD (`TagController`/`TagService`, 2026-07-23)**: added `GET /api/tags` and `POST /api/tags`, reusing `NameValidator` and the same 409-on-duplicate pattern as tables/columns.
  Ruled out: leaving tag creation out entirely (tags already existed as an entity referenced by `Kolon`, but nothing could ever create one).
  Why: `changeKolonTag` only ever looked up an existing tag by id - there was no way to get a tag into the database through the API at all, so "change a column's tag" was unusable end-to-end without this.

- **CORS (`WebConfig`)**: `@Configuration` implementing `WebMvcConfigurer`, allowing `http://localhost:3000` on `/api/**` for GET/POST/PATCH/DELETE.
  Ruled out: `@CrossOrigin` annotations on each controller.
  Why: one place to allow the frontend's origin instead of repeating it per controller; the browser silently blocks the frontend's fetch calls without this.

## Testing

- **Shared Postgres across integration tests**: one `static` `PostgreSQLContainer` in an `AbstractIntegrationTest` base class, extended by every integration test.
  Ruled out: a fresh `@Container` per test class.
  Why: container startup is paid once for the whole test run instead of once per class; this is Testcontainers' own recommended singleton-container pattern.

- **Correction (2026-07-23) - the `@Testcontainers` + `@Container` combo above was not actually a singleton**: switched to a plain `static` initializer block (`static { POSTGRES.start(); }`), no `@Testcontainers`/`@Container` annotations.
  Ruled out: keeping `@Testcontainers`/`@Container` on the static field.
  Why: adding a 5th test class (`TagControllerIntegrationTest`) exposed that the annotation-based approach was silently starting a brand-new container per test class instead of reusing one (visible in the logs: four different containers, four different mapped ports, in a single `mvn test` run). The last class's container connection then failed under the accumulated slowdown, turning into 30s connection-timeout errors. A plain `static` initializer block is Testcontainers' documented fallback for this exact case: it runs exactly once when the class first loads, with no dependency on how the JUnit5 extension discovers `@Container` fields across subclasses. Fix cut full-suite test time from ~2m15s to ~7s.

- **Test data isolation**: once the container was genuinely shared, a real cross-test collision surfaced (two unrelated tests both inserting a tag named "onemli") and failed with 409 instead of 201.
  Ruled out: adding `@Transactional` + rollback to integration tests to auto-isolate them.
  Why: these tests intentionally commit real data to verify the metadata/real-table sync (see the `information_schema` assertions above) - rollback-per-test would undermine that. Simplest correct fix was giving each test's fixture data a name unique enough not to collide with any other test in the suite.

- **Integration test assertions**: query `information_schema.tables`/`information_schema.columns` directly via `JdbcTemplate`, in addition to checking the `Tablo`/`Kolon` metadata rows.
  Ruled out: asserting only against the JPA repositories/entities.
  Why: the whole point of this project is that metadata and the real database object stay in sync; a test that only checks metadata would still pass if the real `CREATE TABLE` silently failed or drifted from what the metadata claims.

- **`BackendApplicationTests`**: made it extend `AbstractIntegrationTest` instead of leaving it a bare `@SpringBootTest`.
  Ruled out: deleting the placeholder now that real tests exist.
  Why: it's a legitimate "does the whole app context boot" smoke test; it just had no datasource to boot against outside of docker compose, which Testcontainers now provides.

## Frontend

- **Layout**: minimal admin/dashboard - left sidebar (table list), right detail panel (columns + tag), modal for table creation.
  Ruled out: a marketing-site-style landing page, or no visual system at all (bare unstyled HTML).
  Why: DBAdmin is an internal tool, not a product marketing site - the assignment explicitly says polish is not expected, only that every operation has a visible UI counterpart. A dashboard layout is the standard shape for this kind of tool (phpMyAdmin/Supabase/Prisma Studio all use it) and needed no extra library.

- **Notifications**: colour derived directly from the HTTP status code returned (`<300` green, `409` amber, anything else red), via a small `NotificationProvider` context.
  Ruled out: a fixed "success"/"error" boolean with no distinction for conflicts.
  Why: the assignment explicitly requires the HTTP status to be visibly reflected in the UI (success vs. client error vs. conflict), not just "did it work or not."

- **API error shape on the frontend (`ApiError`)**: a small class carrying `status` + `message`, thrown by the fetch wrapper whenever `response.ok` is false.
  Ruled out: returning `null`/`undefined` on failure and checking for it at each call site.
  Why: every call site needs the HTTP status to color its notification correctly; throwing keeps that data attached to the error instead of re-deriving it, and lets normal `try/catch` control flow handle both success and failure paths.

- **Full docker-compose verification (2026-07-23)**: after building out the dashboard, ran `docker compose up -d --build` for all three services together (not just `db`+`backend` as during earlier development) and exercised create/list/delete through the nginx-served production build at `localhost:3000`.
  Ruled out: only ever testing the frontend via `npm start` (webpack dev server) against a manually-started backend.
  Why: `npm start` and the real Docker image are different builds (dev server vs. static files behind nginx) - the assignment's actual acceptance bar is "comes up cleanly via `docker compose up`", so that's the thing that needed to be tested, not just the dev workflow.

## Internationalization (i18n) (2026-07-24)

- **i18n library**: `react-i18next` + `i18next`, with a `LanguageSwitcher` (TR/EN) in the header and the choice persisted to `localStorage`.
  Ruled out: a hand-rolled Context provider (same pattern as `NotificationProvider`) with a plain key→string lookup object.
  Why: mentor explicitly asked for a "real" i18n setup; `react-i18next` also gives interpolation (`{{name}}`) for free, which the error-message translations below depend on. Pinned to `react-i18next@14`/`i18next@23` because the newer `react-i18next@17` requires TypeScript ^5, and this project is still on CRA's default TS 4.9.

- **Backend error translation (`code` + `details` on `ErrorResponse`)**: exceptions (`ValidationException`/`ConflictException`/`NotFoundException`) now carry a machine-readable `code` (e.g. `CONFLICT_DUPLICATE_TABLE_NAME`) and a `details` map (e.g. `{"name": "school"}`) alongside the existing English `message`.
  Ruled out: translating the message on the backend itself (Spring `MessageSource` + `Accept-Language` header negotiation).
  Why: the frontend already owns language switching and needs no round-trip to change it; keeping `message` in English also means logs/Postman/`curl` stay readable regardless of what language a client is running in. The backend's job is just to name *what* went wrong (`code`) and hand over the variable parts (`details`) - the frontend decides how to say it.

- **Unknown/unrecognized error codes**: frontend calls `t('errors.' + code, { ...details, defaultValue: message })`.
  Ruled out: treating a missing translation as an error, or always showing the raw code string.
  Why: if a future backend error is added without a matching translation key yet, this falls back to the backend's plain English message instead of showing a broken `errors.SOME_CODE` string to the user - degrades gracefully instead of breaking.

- **Native `required` field validation messages**: overridden per-input via `input.setCustomValidity(t('validation.required'))` on `onInvalid`, cleared on `onChange`.
  Ruled out: dropping the `required` attribute and re-implementing "field is empty" validation by hand (state + conditional error text).
  Why: discovered that Chrome's native validation bubble text follows the *browser's* own UI language setting, not the page's `lang` attribute or the app's selected language - so even with `document.documentElement.lang` set correctly, the bubble stayed in whatever language Chrome itself was in. `setCustomValidity` keeps the native behaviour (focus, submit-blocking) while making the displayed text follow the app's language instead.

## "public" schema'sinin gizlenmesi (2026-07-28)

- **`public` DBAdmin'in API yuzeyinde hic yok**: listelenmez, id'siyle sorulursa 404 doner, bu isimle schema olusturulamaz, oraya tablo kurulamaz/tasinamaz. Metadata'daki (`sema`) `public` satiri da tamamen kaldirildi — `SchemaBootstrapRunner` silindi, artik boyle bir satir hic olusturulmuyor.
  Ruled out: satiri tutup sadece "silinemez/yeniden adlandirilamaz" diye isaretlemek (onceki hal), ya da `public`'i normal bir schema gibi gostermeye devam etmek.
  Why: `public` altyapiya ait — uygulamanin kendi metadata tablolari (`tablo`, `kolon`, `sema`, `tag`) ve ileride kurulacak extension'lar orada duruyor. Arayuzde gorunur olmasi bir yana, `DROP SCHEMA public CASCADE` uygulamanin kendisini silerdi. Sadece "silinemez" demek yetmiyordu: gorunur oldugu surece kullanici oraya tablo kurabiliyor, DBAdmin de kendi metadata tablolarini kullaniciya normal tablo gibi gosterebiliyordu.

- **Savunma katmani**: satir kaldirilmis olsa bile `SchemaService.isHidden` kontrolu duruyor; eski bir kurulumda `sema` icinde `public` satiri kalmissa API onu yine yokmus gibi gosterir.
  Ruled out: "satir zaten yok" varsayip kontrolleri tamamen silmek.
  Why: kontrolun maliyeti bir string karsilastirmasi; kacirildiginda bedeli `DROP SCHEMA public CASCADE`.

- **`createTablo`'da `schemaId` artik zorunlu** (eskiden null gelirse tablo sessizce `public`'e kuruluyordu).
  Ruled out: null gelince ilk schema'yi secmek gibi bir varsayilan.
  Why: `public` gizlendigi icin oraya kurulan bir tablo olusturuldugu anda arayuzde gorunmez olurdu — sessizce yanlis yere kurmaktansa 400 donmek daha durust. Hangi schema'ya kuruldugu zaten kullanicinin bilmesi gereken bir bilgi.

- **`public` icinde kalmis eski kullanici tablolari** (7 adet, cogu test artigi) hem metadata'dan hem gercek DB'den silindi; uygulamanin kendi metadata tablolarina dokunulmadi.
  Ruled out: gizleyip birakmak (metadata'da hayalet satirlar kalirdi), ya da gercek bir schema'ya tasimak.
  Why: hepsi atilabilir test verisiydi; tasimak da gizlemek de `public`'i "arka plan" haline getirme amacini yarim birakirdi.
