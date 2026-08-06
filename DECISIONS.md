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

## Gercek composite PRIMARY KEY (2026-07-29)

- **`primaryKey` isaretli kolonlar artik gercek `PRIMARY KEY`**: birden fazla kolon isaretlenirse tek bir bilesik constraint kuruluyor — `CONSTRAINT "tablo_pkey" PRIMARY KEY ("col1", "col2")`.
  Ruled out: onceki hal — gercek PK'yi otomatik `id` kolonunda birakip isaretli kolonlara sadece bir `UNIQUE` constraint kurmak.
  Why: UNIQUE ile PRIMARY KEY ayni sey degil: UNIQUE kolonlar NULL kabul eder (ve Postgres'te birden fazla NULL satiri birbirinden farkli sayilir), PK ise kolonlari otomatik `NOT NULL` yapar. Istenen cikti DBeaver'da elle yazilmis bir `CREATE TABLE ... PRIMARY KEY (col1, col2)` ile ayni gorunmeliydi; "kozmetik PK + gizli gercek PK" ikilisi bunu saglamiyordu.

- **Otomatik `id` kolonu kaldirildi**: gercek tabloda yalnizca kullanicinin tanimladigi kolonlar var.
  Ruled out: `id` kolonunu tutup PK'sini kaldirmak (sadece surrogate key olarak birakmak).
  Why: iki gerekce birlesti — (1) bir tablonun tek bir PK'si olabilir, `id` PK oldugu surece `PRIMARY KEY (col1, col2)` kurulamaz; (2) `notlar`'daki "kolon olarak eklemedigin ekstra kolon olmayacak -> id kolonu" maddesi zaten bunu istiyordu. Sonucu: hicbir kolon isaretlenmezse tablo PK'siz kuruluyor (Postgres buna izin verir), cunku uydurma bir PK eklemek tam da kaldirilan davranisin ta kendisi olurdu.

- **Constraint adi `<tablo>_pkey`** (eski `<tablo>_pk_unique` yerine).
  Ruled out: constraint'i isimsiz birakip Postgres'in kendi adlandirmasina birakmak.
  Why: `_pkey` zaten Postgres'in varsayilan sonekiyle ayni, yani DBeaver ciktisi elle yazilmis DDL'den ayirt edilemiyor; ama ismi kendimiz uretince `renameTablo` sirasinda constraint'i tabloyla birlikte yeniden adlandirabiliyoruz (`ALTER TABLE ... RENAME CONSTRAINT`) ve PK setini degistirirken hangi constraint'i drop edecegimizi bilebiliyoruz.

- **PK seti degisince drop + yeniden add** (`syncPrimaryKeyConstraint`), "genislet" diye bir islem yok.
  Ruled out: mevcut constraint'e kolon eklemeye calismak.
  Why: Postgres'te bir tablonun birden fazla PK'si olamaz ve bir PK'ye sonradan kolon eklenemez; tek yol eskisini dusurup yenisini kurmak. Bilinen sinir: tabloda zaten satir varken yeni bir kolonu PK yapmak, o satirlarda kolon NULL olacagi icin hata verir — transaction geri alindigi icin metadata da yazilmaz.

- **Var olan tablolar da yeni yapiya tasindi** (`backend/migration/2026-07-29-composite-pk.sql`).
  Ruled out: degisikligi sadece yeni olusturulan tablolara uygulayip eskileri oldugu gibi birakmak.
  Why: ayni uygulamada iki farkli tablo yapisi (eskiler `id` PK + `_pk_unique`, yeniler composite PK) tutarsizlik demek — DBeaver'da bakan biri hangi tablonun hangi kurala uydugunu bilemez, ve `renameTablo` gibi kod yollari artik tek bir constraint adlandirmasi (`<tablo>_pkey`) varsayiyor. Script hangi tablolara dokunacagini uygulamanin kendi metadata'sindan (`tablo`/`kolon`/`sema`) okur, PK kolonlarini `kolon.primary_key`'den alir; `public`'teki metadata tablolarina (Hibernate'in yonettigi `tablo`, `kolon`, `sema`, `tag`) dokunmaz — onlarin `id`'si kendi PK'leri olarak kalmali.
  Not: PK constraint'inin eski adi varsayilmadan pg_catalog'dan bulunuyor, cunku yeniden adlandirilmis tablolarda eski isim kalmisti (ör. `ogr34` tablosunun PK'si `ogr2_pkey` adiyla duruyordu).

- **Var olan kolonun PK isareti degistirilebilir**: `PATCH /api/tablolar/{id}/kolonlar/{kolonId}/primary-key`, UI'da salt okunur "PK" rozeti yerine checkbox.
  Ruled out: isareti yalnizca kolon olusturulurken (createTablo/addKolon) verilebilir birakmak — onceki hal.
  Why: bayrak kozmetikken (sadece bir UNIQUE constraint kuruyorken) eksikligi hissedilmiyordu; tablonun gercek PRIMARY KEY'ini belirlemeye baslayinca var olan bir kolonu PK yapmanin tek yolu onu silip yeniden eklemek, yani icindeki veriyi kaybetmek oldu. Uc, zaten yazili olan `syncPrimaryKeyConstraint`'i cagirir (drop + guncel setle yeniden add), yani PK mantigi tek yerde kalir.
  Not: ayni degeri tekrar gondermek no-op — gereksiz DROP/ADD CONSTRAINT calistirmiyoruz. Tabloda satir varken NULL iceren bir kolonu PK yapmak Postgres tarafindan reddedilir; transaction geri alindigi icin metadata'daki isaret de degismez (test: `changeKolonPrimaryKey_columnWithNullValues_isRejectedAndLeavesMetadataUnchanged`).

## Backend i18n (2026-07-29)

- **Hata mesajlarinin dili `Accept-Language` basligindan secilir**; metinler `messages.properties` (Ingilizce, varsayilan) ve `messages_tr.properties` dosyalarinda, anahtarlar hata kodlarinin ta kendisi.
  Ruled out: `?lang=tr` query parametresi ya da cookie tabanli dil secimi.
  Why: HTTP'nin bu is icin zaten standart bir mekanizmasi var ve her istemci (tarayici, Postman, curl) onu kendiliginden gonderiyor — ozel bir parametre uydurmak API'yi cagiran her tarafa ekstra is cikarirdi.

- **Frontend'in kendi cevirisi kaldi**; backend i18n'i onun yerine gecmiyor.
  Ruled out: frontend'in `errors.*` sozlugunu silip backend'den gelen `message`'i dogrudan gostermek (tek kaynak).
  Why: kullanici arayuzde dil degistirdiginde ekranda duran hata mesajinin da aninda degismesi gerekiyor; backend'e baglarsak her dil degisiminde sunucuya yeniden gitmek gerekir ve zaten gosterilmis mesaj eski dilde kalir. Backend i18n'i farkli bir kitle icin: Swagger/Postman/curl, loglar, ileride baglanacak baska istemciler.

- **Sablon sozdizimi `{{name}}`/`{{id}}`**, MessageFormat'in `{0}`'i degil; yer tutucular `GlobalExceptionHandler`'da exception'in `details` map'inden isme gore doldurulur (`MessageSource.getMessage(code, null, locale)` — args null verilince Spring MessageFormat'i hic calistirmaz).
  Ruled out: `{0}` + `details.values().toArray()`.
  Why: `details` bir Map; degerleri diziye cevirmek dogru sonucu ancak tek elemanliyken garanti eder, ikinci bir detay eklendigi gun sessizce yanlis yere yazardi. Isimli yer tutucu sirayla degil anahtarla eslesiyor. Yan fayda: frontend'in `tr.json`'i da ayni sozdizimini kullaniyor, iki dosyadaki metinler birebir karsilastirilabiliyor.

- **Ceviri bulunamazsa exception'in kendi Ingilizce mesajina duser** (`NoSuchMessageException` yakalanir), `spring.messages.fallback-to-system-locale=false`.
  Ruled out: ceviri eksikse hata firlatmak; ya da Spring'in varsayilani olan "sunucunun yerel diline dus".
  Why: yeni bir hata kodu eklenip properties'e yazilmasi unutulursa istek patlamamali, sadece mesaj cevrilmemis kalmali. Sistem diline dusmek ise ayni istegin uygulamanin calistigi makineye gore farkli cevap dondurmesi demekti.

## Spring'in kendi hatalari da uygulamanin sekline cevrildi (2026-07-29)

- **`GlobalExceptionHandler`'a 6 yeni handler eklendi**: `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException`, `HttpRequestMethodNotSupportedException`, `NoResourceFoundException`, `DataIntegrityViolationException`, ve son care olarak genel `Exception`.
  Ruled out: bunlari yakalamadan birakmak (onceki hal).
  Why: bu 3 exception'in disinda kalan her sey (gecersiz path param, bozuk JSON, olmayan yol, yanlis HTTP metodu, DB constraint ihlali, beklenmeyen hatalar) Spring'in kendi varsayilan govdesine ({@code {"status":..,"error":..,"path":..}}) dusuyordu — `code`/`message` olmadigi icin frontend'in `err.code`'a bakan cevirisi bu durumlarda bos kaliyordu. Uygulamanin "her hata ayni sekilde doner" sozu bu 3 exception'in disinda tutulmuyordu.

- **Ham exception mesajlari istemciye hic gitmiyor**, sadece sunucu loguna (`DataIntegrityViolationException` -> WARN, genel `Exception` -> ERROR).
  Ruled out: `ex.getMessage()`'i oldugu gibi `message` alanina koymak.
  Why: Postgres'in ham hata mesaji tablo/kolon/constraint adlarini icerir, Jackson'in parser hatasi ise dahili sinif adlarini; ikisi de istemciye faydali degil, bazen bilgi sizdirir. Jenerik + kendi dilimizdeki mesaj + kod istemciye, ayrinti (stack trace dahil) sunucu loguna.

- **`CONFLICT_COLUMN_NOT_UNIQUE`**, dunku `PATCH .../primary-key`'in belgelenmis-ama-cozulmemis 500'unu kapattı: artik 409 + kendi kodu doner. Ayni senaryo hem servis seviyesinde (`TabloServiceIntegrationTest`) hem HTTP seviyesinde (`ErrorMessageI18nIntegrationTest`) test edilmis durumda.

## Thread panelleri: iki panel birden (2026-07-29)

- **`dbadmin-backend.json`'a iki thread paneli eklendi**, biri degil: "JVM Threads (live / daemon / peak)" ve "JVM Threads by State" (state'e gore stacked).
  Ruled out: sadece `jvm_threads_live_threads` cizen tek bir panel.
  Why: iki panel farkli arizaya bakiyor. Tek cizgi "kac thread var" der ve trafikle orantisiz tirmanan bir egri thread leak'i ele verir; ama o thread'lerin ne yaptigini gizler — 24 thread'in hepsi calisiyor da olabilir, hepsi bir lock'ta bloklanmis da. State kirilimi ise toplam sabitken `blocked`/`waiting` katmaninin buyudugu ani gosterir, yani yavaslamanin *nedenini* (DB bekliyor / lock bekliyor / CPU yetmiyor) ayirt ettirir. Ikisi de ayni metrik ailesinden geldigi icin ekstra maliyeti yok.
  Not: panolarda zaten heap (`JVM Heap Used` + `Used vs Max`) ve connection (`Active DB Connections` + `HikariCP Connections`) icin ayni "ozet stat + ayrintili grafik" ikilisi vardi; thread tarafi bu simetriyi tamamladi. `notlar`'daki 28 Temmuz maddesinin ucundan eksik olan tek parca thread'lerdi.

- **Panel sorgularinda `state` etiketi elle sayilmiyor**, tek bir `jvm_threads_states_threads{application="backend"}` sorgusu + `{{state}}` legend'i kullaniliyor.
  Ruled out: her state icin ayri bir target yazmak (6 sorgu).
  Why: Micrometer state listesini JVM'den aliyor; elle yazilan liste yeni bir state ciktiginda sessizce eksik kalirdi.

## Authentication: Spring Security + JWT + roller (2026-07-29)

- **Kullanicilar veritabaninda** (`kullanici` tablosu, `public` semasinda, diger metadata tablolarinin yaninda), `application.properties`'te sabit kullanici listesi degil.
  Ruled out: `InMemoryUserDetailsManager` ile properties'ten okunan iki-uc kullanici.
  Why: rol atamak, kullanici eklemek/silmek calisma zamaninda yapilabilmeli; properties'teki liste her degisiklikte yeniden derleme + yeniden baslatma isterdi ve parolalar depoya commit'lenirdi. Uygulama zaten veritabani yonetiyor, kullanicinin da orada yasamasi tutarli.

- **Uc rol: VIEWER / EDITOR / ADMIN.** VIEWER sadece GET, EDITOR tum yazma islemleri, ADMIN ek olarak kullanici yonetimi (`/api/kullanicilar`).
  Ruled out: sadece VIEWER + EDITOR (`notlar`'daki maddenin birebir karsiligi).
  Why: iki rolle kullanicilarin nasil yaratilacagi cevapsiz kaliyordu — birinin kullanici yonetebilmesi gerekiyor. ADMIN o bosluğu dolduruyor ve "farkli kullanicilara roller" maddesini gercekci hale getiriyor.

- **JWT icin jjwt (0.13.0)**, Spring'in `oauth2-resource-server`'i degil.
  Ruled out: `spring-boot-starter-oauth2-resource-server` (cok daha az kod, filtre yazmaya gerek yok).
  Why: token'in nasil imzalandigi ve dogrulandigi acikca gorunur olsun istedik — `JwtService` ve `JwtAuthenticationFilter` okunabilir 100 satir; resource-server'da ayni is bir kara kutuda olurdu. Ogrenme amacli bir projede mekanizmanin gorunur olmasi tercih edildi.
  Not: jjwt'nin JSON serilestiricisi (`jjwt-jackson`) Jackson 2'yi kullanir, proje ise Jackson 3'te (`tools.jackson`). Paket adlari farkli oldugu icin ikisi cakismadan yan yana durur.

- **`/actuator/prometheus` ve `/actuator/health` kimlik dogrulamasiz acik birakildi.**
  Ruled out: her seyi korumak (Spring Security'nin varsayilani).
  Why: Prometheus bu ucu 15 saniyede bir kimliksiz kaziyor; kapatilsaydi Grafana panolari **sessizce** boslardi — hata vermeden, sadece veri gelmeyerek. Bu uclar olcum verisi doner, is verisi degil. `SecurityRulesIntegrationTest` bu iki ucun acik kaldigini ayrica test eder, cunku sessiz bozulmayi gurultulu hale getirmek gerekiyordu.

- **401/403 govdeleri `RestSecurityErrorHandler` ile elle uretiliyor.**
  Ruled out: `GlobalExceptionHandler`'a birakmak.
  Why: Spring Security istegi **servlet filtre zincirinde**, DispatcherServlet'e ulasmadan reddeder; `@RestControllerAdvice` ise DispatcherServlet'in icinde calisir, yani bu iki durumu hic gormez. Boyle birakilsaydi "her hata ayni sekilde doner" sozu tam da giris/yetki hatalarinda bozulur, frontend'in `err.code`'a bakan cevirisi bos kalirdi. Dil de elle cozuluyor (`LocaleResolver`'a dogrudan soruluyor), cunku `LocaleContextHolder`'i dolduran DispatcherServlet henuz calismamis oluyor.

- **CORS `WebMvcConfigurer.addCorsMappings` yerine `CorsConfigurationSource` bean'i olarak veriliyor.**
  Ruled out: eski hali birakip guvenlik tarafina ayrica CORS yazmak (iki ayri dogruluk kaynagi).
  Why: guvenlik filtre zinciri MVC'nin CORS ayarini gormuyor; tek bean her iki tarafin da okudugu ortak kaynak oluyor. `Authorization` basligi da acikca izinli hale getirildi — olmasaydi tarayici preflight'ta asil istegi hic gondermezdi.

- **Ilk ADMIN acilista kod tarafindan uretiliyor** (`KullaniciSeeder`), migration SQL'i ile degil.
  Ruled out: `backend/migration/` altina sabit bir INSERT.
  Why: BCrypt her seferinde farkli (rastgele salt'li) hash uretir; SQL'e sabit hash yazmak, o hash'i ureten parolayi da depoya commit'lemek demekti. Seeder yalnizca tabloda **hic kullanici yokken** calisir, yani mevcut bir kurulumda parola sifirlamaz.

- **Son ADMIN silinemez / rolu dusurulemez** (`CONFLICT_LAST_ADMIN`).
  Ruled out: kontrolu atlamak.
  Why: tek admin kendi rolunu dusurup ya da kendini silip kullanici yonetimine bir daha girilemez hale getirebilirdi — geri donusu sadece veritabanina elle mudahaleyle olan bir kilitlenme.

- **`CONFLICT_LAST_ADMIN` HTTP 409'da kaldi (2026-08-03)**, ayri bir 422 Unprocessable Entity kategorisi acilmadi.
  Ruled out: 422 (istek gecerli ama is kurali reddediyor).
  Why: `ConflictException`'in bu projedeki tanimi zaten "istek dogru formatta ama mevcut durumla catisiyor" (bkz. `CONFLICT_DUPLICATE_TABLE_NAME`, `CONFLICT_COLUMN_NOT_UNIQUE`) — son admin durumu da ayni kalibi izliyor: `DELETE /kullanicilar/{id}` sistemde 2 admin varken sorunsuz calisir, sadece *mevcut veri durumuyla* (admin sayisi=1) catisiyor. Yeni bir HTTP status eklemek yerine var olan kategoriyle tutarli kalindi.

- **Testlerde `@WithMockUser` icin `springSecurity()` koprusu elle kuruldu** (`MockMvcSecurityTestConfig`).
  Ruled out: sadece `@WithMockUser` eklemek (calismadi), ya da her testte gercek token uretmek.
  Why: MockMvc'ye guvenlik filtreleri kendiliginden ekleniyor (kimliksiz istek 401 doner, gercek Bearer token calisir) ama testin koydugu kimlik zincire ulasmiyordu — zincirdeki `SecurityContextHolderFilter` baglami kendi deposundan yukleyip ezdigi icin `@WithMockUser` sessizce etkisizdi ve her sey 401 donuyordu. Not: Spring Boot 4'te bu sinif `org.springframework.boot.webmvc.test.autoconfigure` altina tasindi (eskiden `...test.autoconfigure.web.servlet`).
  Ayrica: `AuthIntegrationTest` bilerek **gercek token** kullanir (mock kimlik yok) — giristen token almaya, token'la korunan uca girmeye kadar tum akis uretimdeki yoluyla test edilir.

## Redis: kullanici rol cache'i (2026-07-31)

- **Cache manuel `RedisTemplate` ile yazildi**, Spring'in `@Cacheable`/`@CacheEvict` annotasyonlari degil.
  Ruled out: `spring-boot-starter-cache` + `@Cacheable(value="kullaniciRol", key="#kullaniciAdi")`.
  Why: `notlar`daki hedef Redis'i *ogrenmekti* — annotasyon tabanli cache mekanizmayi (get/put/evict, TTL, ne zaman DB'ye dusuldugu) tamamen gizler. `KullaniciRolCacheService` ile bu adimlarin hepsi acikca kodda goruluyor.

- **Cache `KullaniciDetailsService.loadUserByUsername`'in ICINE degil, `JwtAuthenticationFilter`'a kondu.**
  Ruled out: cache'i `loadUserByUsername` metodunun basina koymak (tek yer, hem login hem her istek icin gecerli olurdu).
  Why: bu metod hem login'de (`AuthenticationManager` parola karsilastirmasi icin `parolaHash`'e ihtiyac duyar) hem her istekte `JwtAuthenticationFilter` tarafindan (JWT zaten kimligi kanitladigi icin parola hic kullanilmaz, `credentials=null` gecilir) cagriliyor. Cache'i oraya koysaydik `parolaHash` da Redis'e yazilirdi — parola hash'i ekstra bir sistemde tutmak gereksiz saldiri yuzeyi. Sadece parolasiz calisan JWT yoluna cache eklendi, `JwtAuthenticationFilter` artik `KullaniciDetailsService` yerine dogrudan `KullaniciService`'i (id+rol donen is servisi) kullaniyor.

- **Redis'te veri yapisi: Hash (`id`, `rol` alanlari), duz string degerlerle** — JSON serilestirme yok.
  Ruled out: `RedisTemplate<String, Object>` + `GenericJackson2JsonRedisSerializer`.
  Why: sadece iki alanlik basit bir kayit icin JSON serilestirici katmani gereksiz karmasiklik. Duz `StringRedisSerializer` ile `redis-cli HGETALL user:role:admin` / RedisInsight'ta dogrudan okunabilir kaliyor — hem is gorur hem ogrenmesi/hata ayiklamasi kolay.

- **TTL 30 dakika + evict birlikte**, sadece TTL degil.
  Ruled out: yalnizca TTL'e guvenip evict yazmamak (basit ama rol degisince en fazla TTL kadar eski yetki gecerli kalirdi).
  Why: `KullaniciService.changeRol`/`deleteKullanici` artik DB degisikligiyle **ayni metodun icinde** `KullaniciRolCacheService.evict()` cagiriyor — tutarliligi asil bu saglar. TTL sadece unutulan bir evict icin guvenlik agi: DB ile Redis en kotu ihtimalle 30 dk arayla senkronize olur, sonsuza kadar degil.

- **Redis erisilemezse fail-open**: `KullaniciRolCacheService`'in her metodu (`get`/`put`/`evict`) hatalari yutup loglar, hicbirini yukari firlatmaz.
  Ruled out: hatayi yukari firlatip istegi 500 ile reddetmek.
  Why: cache bir optimizasyon, bir bagimlilik degil — Redis container'i durdugunda bile uygulama (biraz daha yavas) calismaya devam etmeli. `spring.data.redis.timeout=300ms` de ayni sebeple eklendi: varsayilan Lettuce timeout'u (60sn) fail-open'i pratikte anlamsiz kilacak kadar yavasti (canli test: Redis kapatilinca istekler ~60sn suruyordu, 300ms'ye cekilince ~0.6sn'ye dustu).

- **`PasswordEncoder` bean'i `SecurityConfig`'ten ayri bir `PasswordEncoderConfig`'e tasindi.**
  Ruled out: `SecurityConfig` icinde birakmak (onceki hal).
  Why: `KullaniciService` (PasswordEncoder'a ihtiyac duyar) `JwtAuthenticationFilter`'a baglaninca su dongu olustu: `SecurityConfig -> JwtAuthenticationFilter -> KullaniciService -> PasswordEncoder (SecurityConfig'in bean'i) -> SecurityConfig`. Bean'i bagimsiz bir config'e almak, Spring'in "circular reference" hatasini kok sebepten cozdu (`spring.main.allow-circular-references=true` gibi bir kacis yoluna gerek kalmadi).

- **RedisInsight (`docker-compose.yml`'e `redisinsight` servisi) eklendi.**
  Ruled out: sadece `redis-cli` ile terminalden bakmak.
  Why: Grafana/Prometheus'ta oldugu gibi, cache'in icini gorsel/etkilesimli inceleyebilmek (key'ler, TTL, hash alanlari) ogrenme amaci icin terminale gore daha hizli geri bildirim veriyor. Kalici bir volume ile veri persist etmez — cache zaten kalici olmak zorunda degil.

## Sayisal hata kodlari (2026-08-03)

- **`ErrorResponse`'a yeni bir `errorCode` (int) alani eklendi**, `code` (string) kaldirilmadi — ikisi birlikte donuyor.
  Ruled out: `code` string'ini tamamen sayiyla degistirmek.
  Why: `code` string'i frontend'in i18next ceviri anahtari (`errors.NOT_FOUND_TABLE`) — sayiya cevirmek 32 ceviri anahtarini, tum testleri (`jsonPath("$.code", ...)`) ve Swagger ornek adlarini degistirmek demekti, hem de sayi kendi basina anlam tasimadigi icin (10001 nedir, ayrica bir tabloya bakmadan bilinemez) okunabilirligi dusururdu. Ek bir alan hem "kendi sayimiz olsun" istegini karsiliyor hem hicbir mevcut sistemi bozmuyor.

- **Sayi semasi: `<HTTP status><2-hane sira>`** (ör. `40401` = 404 ailesinin 1. durumu = `NOT_FOUND_TABLE`), rastgele/keyfi bir kategori numarasi degil.
  Ruled out: HTTP'den tamamen bagimsiz keyfi bir numaralandirma (ör. 1000'ler validation, 2000'ler not-found gibi).
  Why: ilk onerilen keyfi sema ("1xxxx = validation") ezberlenmesi gereken ayri bir sozluk gerektiriyordu; HTTP status'u ilk 3 hanede tasimak sayiyi tek basina okunabilir kiliyor (40401 gorunce zaten 404 ailesinde oldugunu biliyorsun). Riski: bir hatanin HTTP status'u ileride degisirse (nadir) sayisi da degismesi gerekir — bu proje olceginde kabul edilebilir bir bagimlilik.

- **Tek merkezi eslesme noktasi: `ErrorCodeRegistry`** (`exception` paketinde, `Map<String,Integer>`), her `throw new XException(code, ...)` satirina sayi eklenmedi.
  Ruled out: her exception constructor'ina ikinci bir `errorCode` parametresi eklemek (30+ throw call site'ini degistirmek).
  Why: `ErrorResponse.of()` zaten tek merkezi cevap uretme noktasi (`GlobalExceptionHandler`/`RestSecurityErrorHandler` disinda hicbir yer `ErrorResponse` construct etmiyor) — sayiyi orada, `code` string'inden otomatik lookup ile eklemek, geri kalan tum kodu degistirmeden calisiyor. Kayitli olmayan bir `code` icin `ErrorCodeRegistry.numberFor` bilerek `IllegalStateException` firlatiyor (sessizce 0 donmuyor) — yeni bir hata kodu eklenip buraya yazilmasi unutulursa istek 500'e duser, sessizce yanlis sayi donmez.

- **`ErrorCodeRegistryTest`**: `ErrorExamples`'taki 32 sabiti reflection'la okuyup her birinin `errorCode`'unun hem registry'yle hem kendi `status` alaniyla (ilk 3 hane) tutarli oldugunu ve hicbir errorCode'un tekrar etmedigini dogruluyor — 32 sayi elle girildigi icin (kopyala-yapistir riski) bu test olmadan bir yazim hatasi fark edilmeden kalabilirdi.

- **Bu turda kesfedilen ayri bir sorun (dokunulmadi): `SecurityRulesIntegrationTest.actuatorHealth_kimliksiz_erisilebilir_kalmali` artik host makineden `./mvnw test` ile calistirildiginda 503 donuyor** (200 bekleniyordu) — sebebi bu degisiklikle ilgisiz: onceki bir commit'te `docker-compose.yml`'de redis'in `ports` eslemesi `expose`'a cevrildi (bkz. "Swagger hata orneklerini... Redis cache hit/miss metrikleri ekle" commit'i), yani Redis artik host'tan `localhost:6379` ile erisilemiyor, sadece docker network'unden. Actuator health Redis'i DOWN gorup 503 donuyor. Ayri bir konu, ayrica ele alinmali.

## OpenTelemetry gozlemlenebilirlik (2026-08-05 / 2026-08-06)

- **OTel Collector eklenmedi — backend Tempo'ya ve Loki'ye dogrudan export ediyor.**
  Ruled out: `plan-otel-implementation.md`'nin Faz 1.1'inde tasarlanan `otel-collector` servisi (tek toplama/yonlendirme noktasi, Redis emsalinde `dbadmin-net` icine kapali).
  Why: Collector'in asil degeri (backend'i belirli bir backend'e — Tempo/Loki — kilitlememek, yonlendirme kararini konfigurasyona tasimak) bu projenin olceginde somut bir kazanc getirmiyor — tek bir trace/log backend'i var ve degismesi planlanmiyor. Ek bir container, ek bir hata noktasi (Faz olarak zaten test edilen Redis fail-open felsefesiyle ayni gerekce: gereksiz bagimlilik eklenmez) ve ek bir config dosyasi (`otel-collector-config.yaml`) demek. Plan'in kendisi de bunu bir opsiyon olarak ongormustu ("Collector olmadan da Tempo/Loki'ye dogrudan export edilebilir"); 2026-08-06'da bilinctli olarak bu yol benimsendi, plan dosyasindaki mimari degismedi ama uygulamada sadelestirildi.

- **`@Cacheable`/`@CacheEvict`'in Redis komutlari icin ozel bir `RedisCacheWriter` (`TracingAwareRedisCacheWriter`) yazildi**, spring-data-redis'in varsayilan `DefaultRedisCacheWriter`'i (`RedisCacheManager.builder(connectionFactory)`) yerine.
  Ruled out: varsayilan `DefaultRedisCacheWriter`'i oldugu gibi birakmak.
  Why: 2026-08-06'da curl + Tempo API ile dogrudan test edilerek bulundu — `DefaultRedisCacheWriter`, Redis baglantisini `connectionFactory.getConnection()` ile DOGRUDAN aliyor (bytecode'da dogrulandi), `RedisTemplate` ise ayni islemi `RedisConnectionUtils.getConnection(factory, ...)` uzerinden yapiyor. Sadece `RedisConnectionUtils` yolundan giden baglantida Redis komutunun span'i aktif trace'e parent'laniyor — `tracer.currentSpan()`'in o an dolu ve doru oldugu (gecici bir tani logu ile dogrulandi) durumlarda bile, `DefaultRedisCacheWriter` ile giden SET/GET komutlari Tempo'da parent'siz, kopuk trace olarak dusuyordu. Bu, `@Cacheable` kullanan uc serviste (`TagService`, `KullaniciService`, `SchemaService`) Req-2.1'in ("Redis cagrilari otomatik child span uretecek") tam karsilanmamasi anlamina geliyordu. `TracingAwareRedisCacheWriter`, bu projenin kullandigi kadarini (lock'suz, senkron: get/put/putIfAbsent/evict/clear) `RedisConnectionUtils` uzerinden yeniden yaziyor — spring-data-redis'in tum ozelliklerini (locking cache writer, batch strategy) kapsamiyor, ihtiyac olursa genisletilir. Duzeltme sonrasi ayni testle (flush + cache miss + cache hit) hem `set` hem `get` komutlarinin `"secured request"` span'inin altinda dogru parent'landigi dogrulandi.

## Kalici audit log (2026-08-06)

- **`TabloService.applyChanges` icin tek bir ozet audit satiri, alt-islemlerin her biri icin ayri ayri degil.**
  Ruled out: `renameTablo`/`addKolon`/`deleteKolon`/... gibi granuler metodlarin her birinin kendi audit satirini yazmasi (applyChanges bunlari sirayla cagirdigi icin, tek bir "Kaydet" tiklamasi N ayri satira bolunurdu).
  Why: kullanicidan gelen istek acikca "kalabalik olur" gerekcesiyle tek satir istedi. Cozum: her granuler metod `...Core` (audit'siz) ve public (audit'li) ciftine ayrildi; `applyChanges` *Core varyantlarini cagirip yaptigi her seyi bir listede toplar, sonda TEK `TABLO_GUNCELLENDI` satiri yazar. Tekil uclardan (ör. `PATCH /{id}`) dogrudan gelen cagrilar hala kendi tek satirlarini yazar.

- **`AuditLogService`, kullanici id'sini bulmak icin `KullaniciService` degil dogrudan `KullaniciRepository` kullaniyor.**
  Ruled out: `KullaniciService`'i enjekte etmek (daha "servis katmani" gorunumlu olurdu).
  Why: `KullaniciService`'in kendisi de (kullanici olusturma/rol degistirme/silme audit'lendigi icin) `AuditLogService`'e bagimli olmak zorunda — `AuditLogService` de `KullaniciService`'e bagimli olsaydi `KullaniciService -> AuditLogService -> KullaniciService` dongusu olusurdu. Ayni kok-neden cozumu daha once `PasswordEncoderConfig` ayrimi icin de kullanilmisti (bkz. yukarida); `allow-circular-references` gibi bir kacis yoluna gidilmedi.

- **Kimliksiz cagrilar (`SecurityContextHolder`'da `Authentication` yokken) `AuditLogService.kaydet` cagrilirsa kullanici adi `"system"` olarak yazilir, hata firlatilmaz.**
  Ruled out: `Authentication`'in her zaman dolu oldugunu varsayip NPE'ye birakmak.
  Why: `KullaniciSeeder`, uygulama aciliminda (henuz hicbir HTTP istegi/kimlik yokken) ilk ADMIN hesabini `KullaniciService.createKullanici` uzerinden olusturuyor — bu, testlerde gercek bir `IllegalStateException`/NPE olarak ortaya cikti (`KullaniciServiceIntegrationTest`, ApplicationContext yuklenemedi). Bu genuine bir senaryo: o an gercekten "sistemin kendisi" bir islem yapiyor, insan bir kullanici degil. `"system"` icin de `kullaniciId` aramasi yapilmiyor (dogrudan null) — DB'de hicbir zaman boyle bir satir olmayacagi icin arama gereksiz bir `NotFoundException` riski tasirdi.

- **`@WithMockUser` kullanan testlerde kullanici adi acikca `"admin"` olarak sabitlendi**, varsayilan (`"user"`) birakilmadi.
  Ruled out: Spring Security'nin `@WithMockUser` varsayilanini (`username = "user"`) oldugu gibi kullanmak.
  Why: `AuditLogService` artik gercek bir kullanici adi arıyor (once Redis cache, sonra DB) — "user" DB'de hic olmadigi icin `NotFoundException` firlatiyor ve fail-closed geregi butun transaction (ör. `POST /api/tablolar`) rollback oluyor, testler 201 yerine 404 aliyordu. `roles=` parametresi kullanici adindan bagimsiz oldugu icin (`username="admin"` + `roles="VIEWER"` gibi) yetki testleri (SecurityRulesIntegrationTest) bundan etkilenmedi — sadece audit'in bulabilecegi gercek bir kullanici adi verildi.

- **`AuditLogRepository.ara` sorgusunda `HedefTip`/`Instant` parametreleri icin JPQL'de acik `CAST(:param AS ...)` kullanildi**, duz `:param IS NULL` degil.
  Ruled out: `WHERE (:hedefTip IS NULL OR ...)` seklinde cast'siz parametre kontrolu (butun diger opsiyonel filtrelerde oldugu gibi).
  Why: Postgres'in JDBC extended query protokolu, bir parametrenin TEK gorundugu yer `? IS NULL` ise tipini cikaramiyor ("could not determine data type of parameter") — bu sadece enum (`hedefTip`) ve zaman damgasi (`bas`/`bit`) parametrelerinde ortaya cikti (`kullaniciId` bir `Long` oldugu icin somut bir esitlik karsilastirmasinda da gectigi ve Hibernate'in `Long` icin varsayilan tip cikarimi calistigi icin sorun cikarmadi). Acik `CAST` bu belirsizligi kaldirdi, canli olarak `GET /api/audit-loglar?hedefTip=SCHEMA` ile dogrulandi.

- **Bu turda bulunan/duzeltilen bir Hibernate `ddl-auto=update` sinirlamasi (dokunulmadi, sadece dev DB'de elle onarildi): `IslemTipi` enum'una `TABLO_GUNCELLENDI` eklenmesi, zaten var olan `audit_log` tablosunun `audit_log_islem_tipi_check` CHECK constraint'ini otomatik guncellemedi.**
  Neden ortaya cikti: `ddl-auto=update` sadece EKSIK tablo/kolonlari ekler, var olan bir CHECK constraint'i yeni enum degerini icerecek sekilde yeniden yazmaz. Sonuc: `useTablolar` (React hook Faz 2) pilot testi sirasinda `PATCH /api/tablolar/{id}/degisiklikler` (applyChanges, TABLO_GUNCELLENDI audit satiri yazan tek yer) her zaman 409 `CONFLICT_COLUMN_NOT_UNIQUE` donuyordu — hata mesaji yaniltici (gercek sebep PK degil, `audit_log` INSERT'inin CHECK constraint'e takilmasiydi, `GlobalExceptionHandler`'in `DataIntegrityViolationException`'i genel bir "veri celismesi" koduna eslemesi yuzunden). Log'daki gercek `ConstraintViolationException`'i okuyarak teshis edildi. Kalici kod degisikligi gerekmiyor (yeni bir `docker compose down -v` ile tablo sifirdan doğru constraint'le kurulur); bu ortamda `ALTER TABLE ... DROP/ADD CONSTRAINT` ile elle senkronize edildi. Ders: bu proje `ddl-auto=update` kullandigi surece, bir enum'a (audit `IslemTipi`, `HedefTip` gibi CHECK constraint'e donusen alanlar) yeni deger eklendiginde, degisen surecin devam eden bir Docker ortaminda calisan gelistirici `docker compose down -v` yapmadikca ayni hatayi tekrar yasayabilir.
