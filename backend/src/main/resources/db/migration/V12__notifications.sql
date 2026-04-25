 CREATE TABLE notifications (
       id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
       organisation_id UUID        NOT NULL REFERENCES organisations(id),
       utilisateur_id  UUID        REFERENCES utilisateurs(id),  -- NULL = broadcast org
       type            VARCHAR(50) NOT NULL,
       titre           VARCHAR(200) NOT NULL,
       message         TEXT        NOT NULL,
       lien            VARCHAR(500),  -- URL de la page concernée ex: /rh/conges/uuid
       lu              BOOLEAN     NOT NULL DEFAULT FALSE,
       created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
   );
   CREATE INDEX idx_notif_user_lu ON notifications(utilisateur_id, lu, created_at DESC);
   CREATE INDEX idx_notif_org     ON notifications(organisation_id, created_at DESC);