-- ============================================================
-- TABLE DES JOBS D'EXPORT (pour les gros exports asynchrones)
-- ============================================================
CREATE TYPE statut_export_job AS ENUM (
    'EN_ATTENTE',   -- en file d'attente
    'EN_COURS',     -- traitement en cours
    'TERMINE',      -- fichier disponible dans MinIO
    'ERREUR',       -- échec, message d'erreur disponible
    'EXPIRE'        -- fichier supprimé de MinIO après TTL
);

CREATE TYPE type_export AS ENUM (
    'NOTE_FRAIS_PDF',            -- note de frais mission en PDF
    'ETAT_PAIE_PDF',             -- état de paie mensuel PDF
    'ETAT_PAIE_EXCEL',           -- état de paie mensuel Excel
    'BUDGET_PREVISIONNEL_PDF',   -- budget prévu vs réalisé PDF
    'BUDGET_PREVISIONNEL_EXCEL', -- budget prévu vs réalisé Excel
    'JOURNAL_AUDIT_PDF',         -- journal d'audit PDF
    'JOURNAL_AUDIT_EXCEL',       -- journal d'audit Excel
    'JOURNAL_AUDIT_CSV'          -- journal d'audit CSV brut
);

CREATE TABLE export_jobs (
    id                  UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id     UUID        NOT NULL REFERENCES organisations(id),
    demande_par         UUID        NOT NULL REFERENCES utilisateurs(id),
    type_export         type_export NOT NULL,

    -- Paramètres de l'export (stockés en JSON pour flexibilité)
    parametres          JSONB       NOT NULL DEFAULT '{}',
    -- Exemples :
    -- NOTE_FRAIS     : {"mission_id": "uuid"}
    -- ETAT_PAIE      : {"annee": 2026, "mois": 3, "service": "Comptabilité"}
    -- BUDGET         : {"annee": 2026, "budget_id": "uuid"}
    -- JOURNAL_AUDIT  : {"date_debut": "2026-01-01", "date_fin": "2026-03-31",
    --                   "entite": "Facture", "action": "UPDATE", "utilisateur_id": "uuid"}

    statut              statut_export_job NOT NULL DEFAULT 'EN_ATTENTE',
    progression         INTEGER     NOT NULL DEFAULT 0,  -- 0 à 100 (%)
    message_erreur      TEXT,

    -- Résultat
    fichier_url         VARCHAR(500),    -- URL MinIO présignée
    nom_fichier         VARCHAR(300),    -- nom suggéré pour le téléchargement
    taille_octets       BIGINT,
    nb_lignes           INTEGER,         -- nb d'enregistrements inclus

    -- TTL : le fichier MinIO est supprimé après cette date
    -- NOTE: Postgres impose une expression IMMUTABLE pour les colonnes GENERATED.
    -- On utilise donc un DEFAULT calculé à l'insertion (équivalent fonctionnel pour TTL).
    expire_a            TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '48 hours'),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_export_jobs_org     ON export_jobs(organisation_id, statut, created_at DESC);
CREATE INDEX idx_export_jobs_user    ON export_jobs(demande_par, created_at DESC);
CREATE INDEX idx_export_jobs_expire  ON export_jobs(expire_a) WHERE statut = 'TERMINE';

-- ============================================================
-- TABLE DE CONFIG DES EXPORTS PAR ORGANISATION
-- ============================================================
CREATE TABLE config_exports (
    id                          UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id             UUID    NOT NULL UNIQUE REFERENCES organisations(id),

    -- En-tête des documents PDF
    logo_url                    VARCHAR(500),  -- URL MinIO du logo (fallback : logo_url de organisations)
    pied_page_mention           VARCHAR(300)   DEFAULT 'Document confidentiel — usage interne',
    couleur_principale          VARCHAR(7)     DEFAULT '#1B3A5C',  -- hex, pour l'en-tête PDF

    -- Seuil asynchrone
    seuil_lignes_sync_pdf       INTEGER        DEFAULT 500,
    seuil_lignes_sync_excel     INTEGER        DEFAULT 5000,

    -- Watermark sur les PDF conformité
    watermark_actif             BOOLEAN        DEFAULT FALSE,
    watermark_texte             VARCHAR(100)   DEFAULT 'CONFIDENTIEL',

    -- Signature sur les exports de paie
    signature_dg_url            VARCHAR(500),  -- signature numérique DG pour bulletins
    cachet_org_url              VARCHAR(500),  -- cachet de l'organisation

    updated_at                  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Config par défaut pour toutes les orgs existantes
INSERT INTO config_exports (organisation_id)
SELECT id FROM organisations WHERE actif = TRUE
ON CONFLICT DO NOTHING;

