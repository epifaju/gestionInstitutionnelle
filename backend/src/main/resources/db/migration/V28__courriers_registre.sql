CREATE TYPE sens_courrier AS ENUM ('ENTREE', 'SORTIE');
CREATE TYPE statut_courrier AS ENUM ('BROUILLON', 'ENREGISTRE', 'ENVOYE', 'RECU', 'CLASSE', 'ANNULE');
CREATE TYPE canal_courrier AS ENUM ('COURRIER_POSTAL', 'EMAIL', 'MAIN_PROPRE', 'AUTRE');

CREATE TABLE courriers (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organisation_id  UUID            NOT NULL REFERENCES organisations(id),
    sens             sens_courrier   NOT NULL,
    statut           statut_courrier NOT NULL DEFAULT 'BROUILLON',
    reference        VARCHAR(100),
    objet            VARCHAR(300)    NOT NULL,
    expediteur       VARCHAR(300),
    destinataire     VARCHAR(300),
    date_courrier    DATE            NOT NULL,
    date_reception   DATE,
    date_envoi       DATE,
    canal            canal_courrier  NOT NULL DEFAULT 'COURRIER_POSTAL',
    notes            TEXT,
    document_id      UUID            REFERENCES documents(id),
    created_by       UUID            REFERENCES utilisateurs(id),
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_courriers_org_sens_statut ON courriers(organisation_id, sens, statut);
CREATE INDEX idx_courriers_org_date ON courriers(organisation_id, date_courrier DESC);
CREATE UNIQUE INDEX uq_courriers_org_reference
    ON courriers(organisation_id, reference)
    WHERE reference IS NOT NULL;
