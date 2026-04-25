CREATE TYPE visibilite_doc AS ENUM ('PRIVE','SERVICE','ORGANISATION','PUBLIC');

   CREATE TABLE documents (
       id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
       organisation_id  UUID          NOT NULL REFERENCES organisations(id),
       titre            VARCHAR(300)  NOT NULL,
       description      TEXT,
       type_document    VARCHAR(100)  NOT NULL,
       -- Types : CONTRAT, FACTURE_JUS, ORDRE_MISSION, RAPPORT, CIRCULAIRE,
       --         NOTE_DIPLOMATIQUE, ACREDITATION, VISA, PASSEPORT, AUTRE
       tags             TEXT[],       -- array PostgreSQL
       fichier_url      VARCHAR(500)  NOT NULL,
       nom_fichier      VARCHAR(300)  NOT NULL,
       taille_octets    BIGINT        NOT NULL,
       mime_type        VARCHAR(100)  NOT NULL,
       version          INTEGER       NOT NULL DEFAULT 1,
       document_parent_id UUID        REFERENCES documents(id),  -- pour versioning
       visibilite       visibilite_doc NOT NULL DEFAULT 'ORGANISATION',
       service_cible    VARCHAR(150), -- si visibilite=SERVICE
       entite_liee_type VARCHAR(100), -- 'Salarie', 'Facture', 'Mission'...
       entite_liee_id   UUID,         -- ID de l'entité liée
       date_expiration  DATE,         -- pour accréditations, visas...
       uploade_par      UUID          REFERENCES utilisateurs(id),
       created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
       updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
   );
   CREATE INDEX idx_doc_org         ON documents(organisation_id, type_document);
   CREATE INDEX idx_doc_entite      ON documents(entite_liee_type, entite_liee_id);
   CREATE INDEX idx_doc_expiration  ON documents(date_expiration) WHERE date_expiration IS NOT NULL;
   CREATE INDEX idx_doc_tags        ON documents USING GIN(tags);
   CREATE INDEX idx_doc_search      ON documents USING GIN(
     to_tsvector('french', titre || ' ' || COALESCE(description,''))
   );

   CREATE TABLE document_acces (
       document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
       utilisateur_id  UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
       peut_modifier   BOOLEAN NOT NULL DEFAULT FALSE,
       peut_supprimer  BOOLEAN NOT NULL DEFAULT FALSE,
       created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
       PRIMARY KEY (document_id, utilisateur_id)
   );