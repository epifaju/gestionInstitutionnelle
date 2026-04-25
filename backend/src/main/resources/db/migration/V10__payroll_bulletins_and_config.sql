-- ============================================================
-- PAYROLL (France) — Paramétrage admin + Bulletins (snapshot)
-- ============================================================

-- Employer legal settings (one per organisation)
CREATE TABLE IF NOT EXISTS payroll_employer_settings (
    organisation_id UUID PRIMARY KEY REFERENCES organisations(id) ON DELETE CASCADE,
    raison_sociale   VARCHAR(200) NOT NULL,
    adresse_ligne1   VARCHAR(200),
    adresse_ligne2   VARCHAR(200),
    code_postal      VARCHAR(20),
    ville            VARCHAR(100),
    pays             VARCHAR(100),
    siret            VARCHAR(20),
    naf              VARCHAR(10),
    urssaf           VARCHAR(100),
    convention_code  VARCHAR(50),
    convention_libelle VARCHAR(200),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Employee payroll profile (per salarié)
CREATE TABLE IF NOT EXISTS employee_payroll_profile (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    salarie_id      UUID NOT NULL REFERENCES salaries(id) ON DELETE CASCADE,
    cadre           BOOLEAN NOT NULL DEFAULT FALSE,
    convention_code VARCHAR(50),
    convention_libelle VARCHAR(200),
    taux_pas        NUMERIC(6,4), -- ex 0.0730 for 7.30%
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, salarie_id)
);

-- Legal ceilings / constants (versioned by effective date)
CREATE TABLE IF NOT EXISTS payroll_legal_constants (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    code            VARCHAR(50) NOT NULL, -- ex: PMSS
    libelle         VARCHAR(150) NOT NULL,
    valeur          NUMERIC(14,4) NOT NULL,
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, code, effective_from)
);

-- Payroll rubriques (earnings/deductions/info)
CREATE TABLE IF NOT EXISTS payroll_rubriques (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    code            VARCHAR(50) NOT NULL,
    libelle         VARCHAR(150) NOT NULL,
    type            VARCHAR(20) NOT NULL, -- GAIN, RETENUE, INFO
    mode_calcul     VARCHAR(30) NOT NULL, -- FIXED, PERCENT_BASE
    base_code       VARCHAR(50),          -- e.g. BASE_BRUT, BASE_BRUT_PLAFONNE
    taux_salarial   NUMERIC(10,6),
    taux_patronal   NUMERIC(10,6),
    montant_fixe    NUMERIC(12,2),
    ordre_affichage INTEGER NOT NULL DEFAULT 100,
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, code, effective_from)
);

-- Payroll cotisations (social contributions)
CREATE TABLE IF NOT EXISTS payroll_cotisations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    code            VARCHAR(50) NOT NULL,
    libelle         VARCHAR(150) NOT NULL,
    organisme       VARCHAR(100),
    assiette_base_code VARCHAR(50) NOT NULL, -- BASE_BRUT, BASE_BRUT_PLAFONNE, BASE_NET_IMPOSABLE, etc.
    taux_salarial   NUMERIC(10,6),
    taux_patronal   NUMERIC(10,6),
    plafond_code    VARCHAR(50), -- ex PMSS (optional)
    applies_cadre_only BOOLEAN NOT NULL DEFAULT FALSE,
    applies_non_cadre_only BOOLEAN NOT NULL DEFAULT FALSE,
    ordre_affichage INTEGER NOT NULL DEFAULT 100,
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, code, effective_from)
);

-- Bulletins (snapshot) — one per salarié/month once paid
CREATE TABLE IF NOT EXISTS bulletins_paie (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    salarie_id      UUID NOT NULL REFERENCES salaries(id) ON DELETE CASCADE,
    annee           INTEGER NOT NULL,
    mois            INTEGER NOT NULL CHECK (mois BETWEEN 1 AND 12),
    date_paiement   DATE NOT NULL,
    devise          VARCHAR(3) NOT NULL DEFAULT 'EUR',
    cadre           BOOLEAN NOT NULL DEFAULT FALSE,
    convention_code VARCHAR(50),
    convention_libelle VARCHAR(200),
    brut            NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_imposable   NUMERIC(12,2) NOT NULL DEFAULT 0,
    pas_taux        NUMERIC(6,4),
    pas_montant     NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_a_payer     NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_cot_sal   NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_cot_pat   NUMERIC(12,2) NOT NULL DEFAULT 0,
    pdf_object_name VARCHAR(500),
    pdf_generated_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, salarie_id, annee, mois)
);

CREATE TABLE IF NOT EXISTS bulletin_lignes (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    bulletin_id     UUID NOT NULL REFERENCES bulletins_paie(id) ON DELETE CASCADE,
    section         VARCHAR(30) NOT NULL, -- REMUNERATION, COTISATIONS, IMPOT, NET, INFO
    code            VARCHAR(50),
    libelle         VARCHAR(200) NOT NULL,
    base            NUMERIC(12,2),
    taux_salarial   NUMERIC(10,6),
    montant_salarial NUMERIC(12,2),
    taux_patronal   NUMERIC(10,6),
    montant_patronal NUMERIC(12,2),
    ordre_affichage INTEGER NOT NULL DEFAULT 100
);

-- Link paiement salaire -> bulletin
ALTER TABLE paiements_salaires
    ADD COLUMN IF NOT EXISTS bulletin_id UUID REFERENCES bulletins_paie(id);

CREATE INDEX IF NOT EXISTS idx_bulletins_org_salarie_date ON bulletins_paie(organisation_id, salarie_id, annee, mois);
CREATE INDEX IF NOT EXISTS idx_bulletin_lignes_bulletin ON bulletin_lignes(bulletin_id, ordre_affichage);

