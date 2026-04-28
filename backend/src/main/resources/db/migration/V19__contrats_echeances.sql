-- ============================================================
-- ENUMS
-- ============================================================
CREATE TYPE statut_echeance AS ENUM (
    'A_VENIR',      -- date_echeance > aujourd'hui, aucune action lancée
    'EN_ALERTE',    -- dans la fenêtre d'alerte (ex : J-30)
    'ACTION_REQUISE',-- délai critique (ex : J-7)
    'TRAITEE',      -- renouvellement ou action effectuée
    'EXPIREE',      -- date dépassée sans action
    'ANNULEE'       -- annulée manuellement (ex : salarié parti)
);

CREATE TYPE type_echeance AS ENUM (
    'FIN_CDD',              -- fin de contrat CDD
    'FIN_PERIODE_ESSAI',    -- fin de période d'essai
    'RENOUVELLEMENT_CDD',   -- renouvellement d'un CDD avant expiration
    'VISITE_MEDICALE',      -- visite médicale périodique ou d'embauche
    'TITRE_SEJOUR',         -- titre de séjour / permis de travail
    'FORMATION_OBLIGATOIRE',-- formation réglementaire (SST, habilitations...)
    'AVENANT_CONTRAT',      -- avenant à signer
    'AUTRE'                 -- autre échéance RH
);

CREATE TYPE decision_fin_cdd AS ENUM (
    'RENOUVELLEMENT',   -- renouvellement CDD
    'CDI',              -- transformation en CDI
    'NON_RENOUVELE',    -- fin de contrat
    'EN_ATTENTE'        -- décision non encore prise
);

CREATE TYPE statut_visite AS ENUM (
    'PLANIFIEE',
    'REALISEE',
    'REPORTEE',
    'REFUSEE_SALARIE'
);

-- ============================================================
-- TABLE PRINCIPALE : CONTRATS (extension de salaries)
-- ============================================================
-- Stocke les informations contractuelles détaillées pour chaque salarié
-- Ne duplique pas les données de la table salaries existante
CREATE TABLE contrats_salaries (
    id                    UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id       UUID        NOT NULL REFERENCES organisations(id),
    salarie_id            UUID        NOT NULL REFERENCES salaries(id) ON DELETE CASCADE,

    -- Informations contractuelles
    type_contrat          VARCHAR(50) NOT NULL,  -- miroir de salaries.type_contrat au moment de la création
    date_debut_contrat    DATE        NOT NULL,
    date_fin_contrat      DATE,                  -- NULL si CDI
    date_fin_periode_essai DATE,
    duree_essai_mois      INTEGER,
    numero_contrat        VARCHAR(100),           -- référence interne ou officielle
    intitule_poste        VARCHAR(200),           -- poste exact sur le contrat
    motif_cdd             TEXT,                  -- obligatoire si CDD (remplacement, accroissement...)
    convention_collective VARCHAR(200),

    -- Renouvellement CDD
    renouvellement_numero INTEGER     NOT NULL DEFAULT 0,  -- 0 = contrat initial, 1 = 1er renouvellement...
    contrat_parent_id     UUID        REFERENCES contrats_salaries(id),  -- lien vers le CDD précédent
    decision_fin          decision_fin_cdd NOT NULL DEFAULT 'EN_ATTENTE',
    date_decision         DATE,
    commentaire_decision  TEXT,

    -- Documents
    contrat_signe_url     VARCHAR(500),
    avenant_url           VARCHAR(500),

    -- Statut
    actif                 BOOLEAN     NOT NULL DEFAULT TRUE,  -- FALSE quand remplacé par un renouvellement

    -- Audit
    created_by            UUID        REFERENCES utilisateurs(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_contrats_salarie    ON contrats_salaries(salarie_id, actif);
CREATE INDEX idx_contrats_org        ON contrats_salaries(organisation_id, type_contrat);
CREATE INDEX idx_contrats_fin        ON contrats_salaries(date_fin_contrat) WHERE date_fin_contrat IS NOT NULL;
CREATE INDEX idx_contrats_essai      ON contrats_salaries(date_fin_periode_essai) WHERE date_fin_periode_essai IS NOT NULL;

-- ============================================================
-- TABLE ÉCHÉANCES
-- ============================================================
CREATE TABLE echeances_rh (
    id                UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id   UUID        NOT NULL REFERENCES organisations(id),
    salarie_id        UUID        NOT NULL REFERENCES salaries(id) ON DELETE CASCADE,
    contrat_id        UUID        REFERENCES contrats_salaries(id),  -- NULL si pas liée à un contrat

    type_echeance     type_echeance NOT NULL,
    titre             VARCHAR(300) NOT NULL,   -- libellé court affiché dans l'UI
    description       TEXT,                   -- détails/instructions

    date_echeance     DATE        NOT NULL,    -- date limite absolue
    date_alerte_j30   DATE        GENERATED ALWAYS AS (date_echeance - INTERVAL '30 days') STORED,
    date_alerte_j7    DATE        GENERATED ALWAYS AS (date_echeance - INTERVAL '7 days') STORED,

    statut            statut_echeance NOT NULL DEFAULT 'A_VENIR',
    priorite          INTEGER     NOT NULL DEFAULT 2,  -- 1=HAUTE 2=NORMALE 3=BASSE

    -- Responsable du suivi
    responsable_id    UUID        REFERENCES utilisateurs(id),

    -- Action réalisée
    date_traitement   DATE,
    commentaire_traitement TEXT,
    traite_par        UUID        REFERENCES utilisateurs(id),
    document_preuve_url VARCHAR(500),  -- justificatif de traitement

    -- Rappels envoyés (pour éviter les doublons de notifications)
    rappel_j30_envoye BOOLEAN     NOT NULL DEFAULT FALSE,
    rappel_j7_envoye  BOOLEAN     NOT NULL DEFAULT FALSE,
    rappel_j0_envoye  BOOLEAN     NOT NULL DEFAULT FALSE,

    created_by        UUID        REFERENCES utilisateurs(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_echeances_salarie   ON echeances_rh(salarie_id, statut);
CREATE INDEX idx_echeances_org       ON echeances_rh(organisation_id, statut, date_echeance);
CREATE INDEX idx_echeances_date      ON echeances_rh(date_echeance ASC) WHERE statut NOT IN ('TRAITEE','ANNULEE','EXPIREE');
CREATE INDEX idx_echeances_alerte30  ON echeances_rh(date_alerte_j30) WHERE rappel_j30_envoye = FALSE;
CREATE INDEX idx_echeances_alerte7   ON echeances_rh(date_alerte_j7)  WHERE rappel_j7_envoye  = FALSE;
CREATE INDEX idx_echeances_type      ON echeances_rh(organisation_id, type_echeance);

-- ============================================================
-- TABLE VISITES MÉDICALES
-- ============================================================
CREATE TABLE visites_medicales (
    id                UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id   UUID        NOT NULL REFERENCES organisations(id),
    salarie_id        UUID        NOT NULL REFERENCES salaries(id) ON DELETE CASCADE,
    echeance_id       UUID        REFERENCES echeances_rh(id),

    type_visite       VARCHAR(100) NOT NULL,  -- 'EMBAUCHE','PERIODIQUE','REPRISE','SPONTANEE','RENFORCEE'
    date_planifiee    DATE,
    date_realisee     DATE,
    medecin           VARCHAR(200),
    centre_medical    VARCHAR(200),
    statut            statut_visite NOT NULL DEFAULT 'PLANIFIEE',
    resultat          VARCHAR(50),   -- 'APTE','APTE_AMENAGEMENT','INAPTE','EN_ATTENTE'
    restrictions      TEXT,          -- si APTE_AMENAGEMENT : description des restrictions
    prochaine_visite  DATE,          -- calculée automatiquement selon périodicité
    periodicite_mois  INTEGER NOT NULL DEFAULT 24,  -- 24 mois par défaut
    compte_rendu_url  VARCHAR(500),
    notes             TEXT,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_visites_salarie     ON visites_medicales(salarie_id);
CREATE INDEX idx_visites_prochaine   ON visites_medicales(prochaine_visite) WHERE prochaine_visite IS NOT NULL;

-- ============================================================
-- TABLE TITRES DE SÉJOUR & DOCUMENTS ADMINISTRATIFS
-- ============================================================
CREATE TABLE titres_sejour (
    id                UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id   UUID        NOT NULL REFERENCES organisations(id),
    salarie_id        UUID        NOT NULL REFERENCES salaries(id) ON DELETE CASCADE,
    echeance_id       UUID        REFERENCES echeances_rh(id),

    type_document     VARCHAR(100) NOT NULL,  -- 'TITRE_SEJOUR','PERMIS_TRAVAIL','VISA_LONG_SEJOUR','CARTE_RESIDENT','PASSEPORT_SERVICE'
    numero_document   VARCHAR(100),
    pays_emetteur     VARCHAR(100),
    date_emission     DATE,
    date_expiration   DATE        NOT NULL,
    autorite_emettrice VARCHAR(200),
    document_url      VARCHAR(500),  -- scan stocké dans MinIO
    statut_renouvellement VARCHAR(50) DEFAULT 'NON_ENGAGE',  -- NON_ENGAGE, EN_COURS, OBTENU, REFUSE
    notes             TEXT,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_titres_salarie      ON titres_sejour(salarie_id);
CREATE INDEX idx_titres_expiration   ON titres_sejour(date_expiration ASC);

-- ============================================================
-- TABLE FORMATIONS OBLIGATOIRES
-- ============================================================
CREATE TABLE formations_obligatoires (
    id                UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id   UUID        NOT NULL REFERENCES organisations(id),
    salarie_id        UUID        NOT NULL REFERENCES salaries(id) ON DELETE CASCADE,
    echeance_id       UUID        REFERENCES echeances_rh(id),

    intitule          VARCHAR(300) NOT NULL,
    type_formation    VARCHAR(100) NOT NULL,  -- 'SST','HABILITATION_ELEC','INCENDIE','CONDUITE','SECURITE','AUTRE'
    organisme         VARCHAR(200),
    date_realisation  DATE,
    date_expiration   DATE        NOT NULL,  -- date à laquelle la certification expire
    periodicite_mois  INTEGER,               -- NULL = non périodique
    numero_certificat VARCHAR(100),
    certificat_url    VARCHAR(500),
    statut            VARCHAR(50) NOT NULL DEFAULT 'A_REALISER',  -- A_REALISER, PLANIFIEE, REALISEE, EXPIREE
    cout              NUMERIC(10,2),
    notes             TEXT,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_formations_salarie   ON formations_obligatoires(salarie_id);
CREATE INDEX idx_formations_expiration ON formations_obligatoires(date_expiration ASC);

-- ============================================================
-- TABLE CONFIGURATION DES ALERTES PAR ORGANISATION
-- ============================================================
-- Permet à chaque organisation de configurer ses propres délais d'alerte
CREATE TABLE config_alertes_rh (
    id                    UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id       UUID    NOT NULL UNIQUE REFERENCES organisations(id),

    -- Délais d'alerte en jours (avant l'échéance)
    alerte_fin_cdd_j        INTEGER NOT NULL DEFAULT 60,   -- 60 jours avant fin CDD
    alerte_periode_essai_j  INTEGER NOT NULL DEFAULT 15,   -- 15 jours avant fin période d'essai
    alerte_visite_med_j     INTEGER NOT NULL DEFAULT 30,
    alerte_titre_sejour_j   INTEGER NOT NULL DEFAULT 90,   -- 3 mois avant expiration
    alerte_formation_j      INTEGER NOT NULL DEFAULT 60,

    -- Destinataires des alertes
    notifier_rh             BOOLEAN NOT NULL DEFAULT TRUE,
    notifier_manager        BOOLEAN NOT NULL DEFAULT FALSE,
    notifier_salarie        BOOLEAN NOT NULL DEFAULT FALSE,

    -- Verrouillage (ex : pour CDD, ne pas autoriser > 2 renouvellements)
    max_renouvellements_cdd INTEGER NOT NULL DEFAULT 2,

    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Insérer config par défaut pour toutes les orgs existantes
INSERT INTO config_alertes_rh (organisation_id)
SELECT id FROM organisations WHERE actif = TRUE
ON CONFLICT DO NOTHING;

-- ============================================================
-- VUE : TABLEAU DE BORD DES ÉCHÉANCES
-- ============================================================
-- Utilisée par le dashboard pour afficher les alertes consolidées
CREATE VIEW v_echeances_dashboard AS
SELECT
    e.id,
    e.organisation_id,
    e.salarie_id,
    s.nom || ' ' || s.prenom      AS salarie_nom_complet,
    s.service,
    s.matricule,
    e.type_echeance,
    e.titre,
    e.date_echeance,
    e.statut,
    e.priorite,
    e.responsable_id,
    CURRENT_DATE - e.date_echeance AS jours_retard,   -- positif = dépassé
    e.date_echeance - CURRENT_DATE AS jours_restants, -- positif = à venir
    CASE
        WHEN e.date_echeance < CURRENT_DATE THEN 'CRITIQUE'
        WHEN e.date_echeance <= CURRENT_DATE + INTERVAL '7 days'  THEN 'URGENT'
        WHEN e.date_echeance <= CURRENT_DATE + INTERVAL '30 days' THEN 'ATTENTION'
        ELSE 'NORMAL'
    END AS niveau_urgence
FROM echeances_rh e
JOIN salaries s ON s.id = e.salarie_id
WHERE e.statut NOT IN ('TRAITEE', 'ANNULEE', 'EXPIREE');

-- Fin de V19__contrats_echeances.sql
