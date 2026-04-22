-- Taux de change (manuel) par organisation et date
-- Devise de référence: EUR
CREATE TABLE IF NOT EXISTS taux_change (
    organisation_id UUID        NOT NULL REFERENCES organisations(id),
    date            DATE        NOT NULL,
    devise          VARCHAR(3)  NOT NULL,
    taux_vers_eur   NUMERIC(12,6) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (organisation_id, date, devise),
    CHECK (taux_vers_eur > 0),
    CHECK (devise IN ('EUR','XOF','USD'))
);

CREATE INDEX IF NOT EXISTS idx_taux_change_org_date ON taux_change(organisation_id, date);
