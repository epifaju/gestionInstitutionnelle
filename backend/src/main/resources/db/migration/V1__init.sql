-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Enums
CREATE TYPE role_enum         AS ENUM ('ADMIN','FINANCIER','RH','LOGISTIQUE','EMPLOYE');
CREATE TYPE statut_salarie    AS ENUM ('ACTIF','EN_CONGE','SORTI');
CREATE TYPE statut_conge      AS ENUM ('BROUILLON','EN_ATTENTE','VALIDE','REJETE');
CREATE TYPE type_conge        AS ENUM ('ANNUEL','MALADIE','EXCEPTIONNEL','SANS_SOLDE');
CREATE TYPE statut_paie       AS ENUM ('EN_ATTENTE','PAYE','ANNULE');
CREATE TYPE statut_facture    AS ENUM ('BROUILLON','A_PAYER','PAYE','ANNULE');
CREATE TYPE type_recette      AS ENUM ('FRAIS_SERVICE','ADHESION','DON','SUBVENTION','PRESTATION');
CREATE TYPE statut_budget     AS ENUM ('BROUILLON','VALIDE','CLOTURE');
CREATE TYPE etat_bien         AS ENUM ('BON','USE','DEFAILLANT','HORS_SERVICE');
CREATE TYPE type_mouvement    AS ENUM ('CREATION','AFFECTATION','DEPLACEMENT','ETAT','REFORME');
CREATE TYPE type_categorie    AS ENUM ('DEPENSE','RECETTE');

-- Organisations
CREATE TABLE organisations (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nom                   VARCHAR(200) NOT NULL,
    type                  VARCHAR(50)  NOT NULL,
    pays                  VARCHAR(100),
    devise_defaut         VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    logo_url              VARCHAR(500),
    seuil_justificatif    NUMERIC(12,2) NOT NULL DEFAULT 500.00,
    alerte_budget_pct     INTEGER      NOT NULL DEFAULT 80,
    actif                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Utilisateurs
CREATE TABLE utilisateurs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID        NOT NULL REFERENCES organisations(id),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            role_enum   NOT NULL DEFAULT 'EMPLOYE',
    nom             VARCHAR(100),
    prenom          VARCHAR(100),
    actif           BOOLEAN     NOT NULL DEFAULT TRUE,
    dernier_login   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, email)
);

-- Refresh tokens
CREATE TABLE refresh_tokens (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    utilisateur_id UUID        NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    token_hash     VARCHAR(255) NOT NULL UNIQUE,
    expires_at     TIMESTAMPTZ NOT NULL,
    used           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens ON refresh_tokens(token_hash, used);

-- Salariés
CREATE TABLE salaries (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID         NOT NULL REFERENCES organisations(id),
    utilisateur_id  UUID         REFERENCES utilisateurs(id),
    matricule       VARCHAR(50)  NOT NULL,
    nom             VARCHAR(100) NOT NULL,
    prenom          VARCHAR(100) NOT NULL,
    email           VARCHAR(255),
    telephone       VARCHAR(30),
    poste           VARCHAR(150) NOT NULL,
    service         VARCHAR(150) NOT NULL,
    date_embauche   DATE         NOT NULL,
    type_contrat    VARCHAR(50)  NOT NULL,
    statut          statut_salarie NOT NULL DEFAULT 'ACTIF',
    nationalite     VARCHAR(100),
    adresse         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, matricule)
);
CREATE TABLE historique_salaires (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    salarie_id   UUID          NOT NULL REFERENCES salaries(id),
    montant_brut NUMERIC(12,2) NOT NULL,
    montant_net  NUMERIC(12,2) NOT NULL,
    devise       VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    date_debut   DATE          NOT NULL,
    date_fin     DATE,
    notes        TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE TABLE paiements_salaires (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID         NOT NULL REFERENCES organisations(id),
    salarie_id      UUID         NOT NULL REFERENCES salaries(id),
    mois            INTEGER      NOT NULL CHECK (mois BETWEEN 1 AND 12),
    annee           INTEGER      NOT NULL,
    montant         NUMERIC(12,2) NOT NULL,
    devise          VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    date_paiement   DATE,
    mode_paiement   VARCHAR(50),
    statut          statut_paie  NOT NULL DEFAULT 'EN_ATTENTE',
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(salarie_id, mois, annee)
);
CREATE TABLE conges_absences (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID           NOT NULL REFERENCES organisations(id),
    salarie_id      UUID           NOT NULL REFERENCES salaries(id),
    type_conge      type_conge     NOT NULL,
    date_debut      DATE           NOT NULL,
    date_fin        DATE           NOT NULL,
    nb_jours        NUMERIC(5,1)   NOT NULL,
    statut          statut_conge   NOT NULL DEFAULT 'BROUILLON',
    valideur_id     UUID           REFERENCES utilisateurs(id),
    date_validation TIMESTAMPTZ,
    motif_rejet     TEXT,
    commentaire     TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CHECK (date_fin >= date_debut)
);
CREATE TABLE droits_conges (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID          NOT NULL REFERENCES organisations(id),
    salarie_id      UUID          NOT NULL REFERENCES salaries(id),
    annee           INTEGER       NOT NULL,
    jours_droit     NUMERIC(5,1)  NOT NULL DEFAULT 30,
    jours_pris      NUMERIC(5,1)  NOT NULL DEFAULT 0,
    jours_restants  NUMERIC(5,1)  NOT NULL DEFAULT 30,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE(salarie_id, annee)
);
CREATE TABLE categories_depenses (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID           NOT NULL REFERENCES organisations(id),
    libelle         VARCHAR(150)   NOT NULL,
    code            VARCHAR(20)    NOT NULL,
    type            type_categorie NOT NULL,
    couleur         VARCHAR(7)     DEFAULT '#6B7280',
    actif           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, code)
);
CREATE TABLE factures (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id  UUID           NOT NULL REFERENCES organisations(id),
    reference        VARCHAR(30)    NOT NULL,
    fournisseur      VARCHAR(200)   NOT NULL,
    date_facture     DATE           NOT NULL,
    montant_ht       NUMERIC(12,2)  NOT NULL,
    tva              NUMERIC(5,2)   NOT NULL DEFAULT 0,
    montant_ttc      NUMERIC(12,2)  NOT NULL,
    devise           VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    taux_change_eur  NUMERIC(10,6)  NOT NULL DEFAULT 1,
    categorie_id     UUID           REFERENCES categories_depenses(id),
    statut           statut_facture NOT NULL DEFAULT 'BROUILLON',
    justificatif_url VARCHAR(500),
    notes            TEXT,
    created_by       UUID           REFERENCES utilisateurs(id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, reference)
);
CREATE TABLE facture_sequences (
    organisation_id UUID    NOT NULL REFERENCES organisations(id),
    annee           INTEGER NOT NULL,
    derniere_seq    INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (organisation_id, annee)
);
CREATE TABLE paiements (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID          NOT NULL REFERENCES organisations(id),
    date_paiement   DATE          NOT NULL,
    montant_total   NUMERIC(12,2) NOT NULL,
    devise          VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    compte          VARCHAR(100),
    moyen_paiement  VARCHAR(50)   NOT NULL,
    notes           TEXT,
    created_by      UUID          REFERENCES utilisateurs(id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE TABLE facture_paiements (
    facture_id  UUID          NOT NULL REFERENCES factures(id),
    paiement_id UUID          NOT NULL REFERENCES paiements(id),
    montant     NUMERIC(12,2) NOT NULL,
    PRIMARY KEY (facture_id, paiement_id)
);
CREATE TABLE recettes (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id  UUID         NOT NULL REFERENCES organisations(id),
    date_recette     DATE         NOT NULL,
    montant          NUMERIC(12,2) NOT NULL,
    devise           VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    taux_change_eur  NUMERIC(10,6) NOT NULL DEFAULT 1,
    type_recette     type_recette NOT NULL,
    description      TEXT,
    mode_encaissement VARCHAR(50),
    justificatif_url VARCHAR(500),
    categorie_id     UUID         REFERENCES categories_depenses(id),
    created_by       UUID         REFERENCES utilisateurs(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE TABLE budgets_annuels (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID          NOT NULL REFERENCES organisations(id),
    annee           INTEGER       NOT NULL,
    statut          statut_budget NOT NULL DEFAULT 'BROUILLON',
    date_validation TIMESTAMPTZ,
    valideur_id     UUID          REFERENCES utilisateurs(id),
    notes           TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE TABLE lignes_budget (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    budget_id    UUID           NOT NULL REFERENCES budgets_annuels(id) ON DELETE CASCADE,
    categorie_id UUID           NOT NULL REFERENCES categories_depenses(id),
    type         type_categorie NOT NULL,
    montant_prevu NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE(budget_id, categorie_id)
);
CREATE VIEW v_execution_budget AS
SELECT lb.id AS ligne_id, lb.budget_id, lb.categorie_id,
       cd.libelle AS categorie_libelle, lb.type, lb.montant_prevu,
       COALESCE(r.montant,0) AS montant_realise,
       lb.montant_prevu - COALESCE(r.montant,0) AS ecart,
       CASE WHEN lb.montant_prevu > 0
            THEN ROUND(COALESCE(r.montant,0)/lb.montant_prevu*100,1)
            ELSE 0 END AS taux_execution_pct
FROM lignes_budget lb
JOIN categories_depenses cd ON cd.id = lb.categorie_id
LEFT JOIN (
    SELECT categorie_id, SUM(montant_ttc) AS montant
    FROM factures WHERE statut IN ('A_PAYER','PAYE') GROUP BY categorie_id
) r ON r.categorie_id = lb.categorie_id AND lb.type = 'DEPENSE';
CREATE TABLE biens_materiels (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id  UUID         NOT NULL REFERENCES organisations(id),
    code_inventaire  VARCHAR(30)  NOT NULL,
    libelle          VARCHAR(200) NOT NULL,
    categorie        VARCHAR(100) NOT NULL,
    code_categorie   VARCHAR(10)  NOT NULL,
    date_acquisition DATE,
    valeur_achat     NUMERIC(12,2) NOT NULL DEFAULT 0,
    devise           VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    localisation     VARCHAR(200),
    etat             etat_bien    NOT NULL DEFAULT 'BON',
    responsable_id   UUID         REFERENCES salaries(id),
    description      TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(organisation_id, code_inventaire)
);
CREATE TABLE bien_sequences (
    organisation_id UUID        NOT NULL REFERENCES organisations(id),
    code_categorie  VARCHAR(10) NOT NULL,
    annee           INTEGER     NOT NULL,
    derniere_seq    INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (organisation_id, code_categorie, annee)
);
CREATE TABLE mouvements_biens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    bien_id         UUID           NOT NULL REFERENCES biens_materiels(id),
    date_mouvement  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    type_mouvement  type_mouvement NOT NULL,
    champ_modifie   VARCHAR(100),
    ancienne_valeur TEXT,
    nouvelle_valeur TEXT,
    motif           TEXT,
    auteur_id       UUID           REFERENCES utilisateurs(id)
);
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id UUID         REFERENCES organisations(id),
    utilisateur_id  UUID         REFERENCES utilisateurs(id),
    action          VARCHAR(20)  NOT NULL,
    entite          VARCHAR(100) NOT NULL,
    entite_id       UUID,
    avant           JSONB,
    apres           JSONB,
    ip_address      VARCHAR(45),
    date_action     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
-- Indexes
CREATE INDEX idx_utilisateurs_org      ON utilisateurs(organisation_id);
CREATE INDEX idx_salaries_org          ON salaries(organisation_id);
CREATE INDEX idx_salaries_statut       ON salaries(organisation_id, statut);
CREATE INDEX idx_conges_salarie        ON conges_absences(salarie_id, statut);
CREATE INDEX idx_conges_dates          ON conges_absences(date_debut, date_fin);
CREATE INDEX idx_factures_org_date     ON factures(organisation_id, date_facture DESC);
CREATE INDEX idx_factures_statut       ON factures(organisation_id, statut);
CREATE INDEX idx_recettes_org_date     ON recettes(organisation_id, date_recette DESC);
CREATE INDEX idx_biens_org_etat        ON biens_materiels(organisation_id, etat);
CREATE INDEX idx_audit_entite          ON audit_logs(entite, entite_id, date_action DESC);
CREATE INDEX idx_salaries_search ON salaries USING gin(to_tsvector('french', nom||' '||prenom||' '||matricule));

-- Données de test (dev)
INSERT INTO organisations (id, nom, type, pays, devise_defaut, actif) VALUES
('a0000000-0000-0000-0000-000000000001'::uuid, 'Ambassade Test', 'AMBASSADE', 'France', 'EUR', true);

INSERT INTO utilisateurs (id, organisation_id, email, password_hash, role, nom, prenom, actif) VALUES
('b0000000-0000-0000-0000-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'admin@test.com', '$2b$12$83uNfBglkdJMt29kbBWF9eOb4/JIoDzyzqKQo0uI6pOKropIm0o36', 'ADMIN', 'Admin', 'Test', true);
