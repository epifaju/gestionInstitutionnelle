-- ============================================================
-- Payroll tables: add updated_at for BaseEntity-backed tables
-- ============================================================

-- These tables are mapped to entities extending BaseEntity (requires updated_at NOT NULL).

ALTER TABLE IF EXISTS payroll_legal_constants
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE IF EXISTS payroll_rubriques
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE IF EXISTS payroll_cotisations
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE IF EXISTS bulletins_paie
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Also ensure updated_at exists where expected (safety; already present in initial migration)
ALTER TABLE IF EXISTS employee_payroll_profile
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

