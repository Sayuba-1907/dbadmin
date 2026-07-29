-- Mevcut kullanici tablolarini yeni primary key yapisina tasir (tek seferlik).
--
-- Neden gerekli: composite PRIMARY KEY degisikligi (bkz. DECISIONS.md, 2026-07-29) sadece
-- yeni olusturulan tablolari etkiliyordu. Bu script eski tablolari da ayni hale getirir:
--   - otomatik eklenmis "id" kolonunu ve onun uzerindeki PRIMARY KEY'i dusurur,
--   - eski "<tablo>_pk_unique" UNIQUE constraint'ini kaldirir,
--   - metadata'da (kolon.primary_key) isaretli kolonlarla gercek PRIMARY KEY'i kurar:
--     tek kolon -> basit PK, birden fazla -> composite PK.
--
-- Hangi tablolara dokundugu tamamen uygulamanin kendi metadata'sindan (tablo/kolon/sema)
-- okunur; public'teki metadata tablolarinin (tablo, kolon, sema, tag) kendisine dokunmaz —
-- onlar Hibernate'in yonettigi entity tablolari, id'leri kendi PK'leri olarak kalmali.
--
-- Calistirma:
--   docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
--     < backend/migration/2026-07-29-composite-pk.sql
--
-- Idempotent: ikinci kez calistirilmasi zarar vermez, ayni sonucu uretir.
-- Dikkat: PK yapilacak kolonlarda NULL ya da tekrar eden deger varsa ADD PRIMARY KEY hata
-- verir ve tum script geri alinir (tek transaction).

BEGIN;

DO $$
DECLARE
    tbl        record;
    con_name   text;
    pk_columns text;
BEGIN
    FOR tbl IN
        SELECT t.id AS tablo_id, s.name AS schema_name, t.name AS table_name
        FROM public.tablo t
        JOIN public.sema s ON s.id = t.schema_id
        ORDER BY s.name, t.name
    LOOP
        -- Gercek tablo yoksa (metadata'da kalmis hayalet satir) atla.
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = tbl.schema_name AND table_name = tbl.table_name
        ) THEN
            RAISE NOTICE 'atlandi (gercek tablo yok): %.%', tbl.schema_name, tbl.table_name;
            CONTINUE;
        END IF;

        -- 1) Var olan PRIMARY KEY'i kaldir. Adini varsaymiyoruz: tablo yeniden
        --    adlandirilmis olabilir (ör. "ogr34" tablosunun PK'sinin adi "ogr2_pkey").
        FOR con_name IN
            SELECT con.conname
            FROM pg_constraint con
            JOIN pg_class c ON c.oid = con.conrelid
            WHERE c.relname = tbl.table_name
              AND c.relnamespace::regnamespace::text = tbl.schema_name
              AND con.contype = 'p'
        LOOP
            EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT %I',
                           tbl.schema_name, tbl.table_name, con_name);
        END LOOP;

        -- 2) Eski yapinin "isaretli kolonlar sadece UNIQUE olsun" constraint'ini kaldir.
        EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT IF EXISTS %I',
                       tbl.schema_name, tbl.table_name, tbl.table_name || '_pk_unique');

        -- 3) Otomatik eklenmis "id" kolonunu dusur — ama sadece metadata'da boyle bir kolon
        --    yoksa: kullanici kendi eliyle "id" adinda bir kolon tanimlamis olabilir, o kalmali.
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = tbl.schema_name AND table_name = tbl.table_name
              AND column_name = 'id'
        ) AND NOT EXISTS (
            SELECT 1 FROM public.kolon k WHERE k.tablo_id = tbl.tablo_id AND k.name = 'id'
        ) THEN
            EXECUTE format('ALTER TABLE %I.%I DROP COLUMN id', tbl.schema_name, tbl.table_name);
        END IF;

        -- 4) Metadata'da PK isaretli kolonlarla gercek PRIMARY KEY'i kur. Kolon sirasi
        --    metadata'daki ekleme sirasi (kolon.id) — composite PK'de sira anlamlidir.
        SELECT string_agg(format('%I', k.name), ', ' ORDER BY k.id)
          INTO pk_columns
          FROM public.kolon k
         WHERE k.tablo_id = tbl.tablo_id AND k.primary_key;

        IF pk_columns IS NULL THEN
            RAISE NOTICE 'PK isaretli kolon yok, tablo PK''siz birakildi: %.%',
                         tbl.schema_name, tbl.table_name;
        ELSE
            EXECUTE format('ALTER TABLE %I.%I ADD CONSTRAINT %I PRIMARY KEY (%s)',
                           tbl.schema_name, tbl.table_name, tbl.table_name || '_pkey', pk_columns);
            RAISE NOTICE 'PRIMARY KEY (%) kuruldu: %.%',
                         pk_columns, tbl.schema_name, tbl.table_name;
        END IF;
    END LOOP;
END $$;

COMMIT;
