-- Rejecting an expense is an audited command: the reason is mandatory and the actor is stored.
ALTER TABLE expenses ADD COLUMN rejected_at TIMESTAMPTZ NULL;
ALTER TABLE expenses ADD COLUMN rejected_by_id VARCHAR(50) NULL;
