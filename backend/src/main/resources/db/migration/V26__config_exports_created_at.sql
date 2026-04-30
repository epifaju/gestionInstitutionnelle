-- Add missing BaseEntity auditing columns for config_exports
ALTER TABLE config_exports
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Ensure updated_at exists and is NOT NULL (defensive)
ALTER TABLE config_exports
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

