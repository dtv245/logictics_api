ALTER TABLE public.employees
    ADD COLUMN IF NOT EXISTS "CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ADD COLUMN IF NOT EXISTS "CreatedBy" varchar(50) NULL,
    ADD COLUMN IF NOT EXISTS "LastModifiedAt" timestamptz NULL,
    ADD COLUMN IF NOT EXISTS "LastModifiedBy" varchar(50) NULL;
