CREATE TYPE statut_mission AS ENUM ('BROUILLON','SOUMISE','APPROUVEE','EN_COURS','TERMINEE','ANNULEE');
   CREATE TYPE statut_frais   AS ENUM ('BROUILLON','SOUMIS','VALIDE','REMBOURSE','REJETE');

   CREATE TABLE missions (
       id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
       organisation_id UUID           NOT NULL REFERENCES organisations(id),
       salarie_id      UUID           NOT NULL REFERENCES salaries(id),
       titre           VARCHAR(200)   NOT NULL,
       destination     VARCHAR(200)   NOT NULL,
       pays_destination VARCHAR(100),
       objectif        TEXT,
       date_depart     DATE           NOT NULL,
       date_retour     DATE           NOT NULL,
       statut          statut_mission NOT NULL DEFAULT 'BROUILLON',
       avance_demandee NUMERIC(12,2)  DEFAULT 0,
       avance_devise   VARCHAR(3)     DEFAULT 'EUR',
       avance_versee   NUMERIC(12,2)  DEFAULT 0,
       approbateur_id  UUID           REFERENCES utilisateurs(id),
       date_approbation TIMESTAMPTZ,
       motif_refus     TEXT,
       ordre_mission_url VARCHAR(500),
       rapport_url     VARCHAR(500),
       created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
       updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
       CHECK (date_retour >= date_depart)
   );

   CREATE TABLE frais_mission (
       id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
       mission_id      UUID           NOT NULL REFERENCES missions(id) ON DELETE CASCADE,
       type_frais      VARCHAR(50)    NOT NULL,  -- TRANSPORT, HEBERGEMENT, REPAS, VISA, AUTRE
       description     VARCHAR(300)   NOT NULL,
       date_frais      DATE           NOT NULL,
       montant         NUMERIC(12,2)  NOT NULL,
       devise          VARCHAR(3)     NOT NULL DEFAULT 'EUR',
       taux_change_eur NUMERIC(10,6)  NOT NULL DEFAULT 1,
       justificatif_url VARCHAR(500),
       statut          statut_frais   NOT NULL DEFAULT 'BROUILLON',
       created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
   );

   CREATE INDEX idx_missions_org      ON missions(organisation_id, statut);
   CREATE INDEX idx_missions_salarie  ON missions(salarie_id);
   CREATE INDEX idx_frais_mission     ON frais_mission(mission_id);