ALTER TABLE public.payments
    ADD COLUMN IF NOT EXISTS tenant_id uuid;
