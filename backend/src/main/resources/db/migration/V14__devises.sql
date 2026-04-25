  CREATE TABLE taux_change_historique (
       id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
       devise_base VARCHAR(3)    NOT NULL DEFAULT 'EUR',
       devise_cible VARCHAR(3)   NOT NULL,
       taux        NUMERIC(12,6) NOT NULL,
       date_taux   DATE          NOT NULL,
       source      VARCHAR(50)   NOT NULL DEFAULT 'API',
       created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
       UNIQUE(devise_base, devise_cible, date_taux)
   );
   CREATE INDEX idx_taux_date ON taux_change_historique(devise_base, devise_cible, date_taux DESC);