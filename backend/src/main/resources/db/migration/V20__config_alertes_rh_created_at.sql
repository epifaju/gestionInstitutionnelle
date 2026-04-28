-- Alignement avec BaseEntity : ConfigAlerteRh hérite de created_at / updated_at
ALTER TABLE config_alertes_rh
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
