-- Forward-only alignment with BaseAuditableEntity and convention v3 section 4.3.
-- Rename only the four legacy audit names. No timestamp/currency/data conversion.
-- Existing V1 checksums are deliberately preserved.
DO $$
DECLARE
    audit_column RECORD;
    canonical_name TEXT;
BEGIN
    FOR audit_column IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND column_name IN ('CreatedAt', 'CreatedBy', 'LastModifiedAt', 'LastModifiedBy')
        ORDER BY table_name, column_name
    LOOP
        canonical_name := CASE audit_column.column_name
            WHEN 'CreatedAt' THEN 'created_at'
            WHEN 'CreatedBy' THEN 'created_by'
            WHEN 'LastModifiedAt' THEN 'last_modified_at'
            WHEN 'LastModifiedBy' THEN 'last_modified_by'
        END;
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = audit_column.table_name
                     AND column_name = canonical_name) THEN
            RAISE EXCEPTION 'Ambiguous audit columns on public.%: % and % both exist',
                audit_column.table_name, audit_column.column_name, canonical_name;
        END IF;
        EXECUTE format('ALTER TABLE public.%I RENAME COLUMN %I TO %I',
            audit_column.table_name, audit_column.column_name, canonical_name);
    END LOOP;
END $$;
