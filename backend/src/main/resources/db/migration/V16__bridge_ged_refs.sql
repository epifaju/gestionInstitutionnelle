-- Bridge compatibility: allow existing modules to point to GED documents (without removing legacy *_url yet).

ALTER TABLE factures
  ADD COLUMN IF NOT EXISTS document_justificatif_id UUID REFERENCES documents(id);

ALTER TABLE recettes
  ADD COLUMN IF NOT EXISTS document_justificatif_id UUID REFERENCES documents(id);

ALTER TABLE missions
  ADD COLUMN IF NOT EXISTS ordre_mission_document_id UUID REFERENCES documents(id),
  ADD COLUMN IF NOT EXISTS rapport_document_id UUID REFERENCES documents(id);

ALTER TABLE frais_mission
  ADD COLUMN IF NOT EXISTS justificatif_document_id UUID REFERENCES documents(id);

CREATE INDEX IF NOT EXISTS idx_factures_doc_justif ON factures(document_justificatif_id) WHERE document_justificatif_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_recettes_doc_justif ON recettes(document_justificatif_id) WHERE document_justificatif_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_missions_doc_ordre  ON missions(ordre_mission_document_id) WHERE ordre_mission_document_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_missions_doc_rapport ON missions(rapport_document_id) WHERE rapport_document_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_frais_doc_justif ON frais_mission(justificatif_document_id) WHERE justificatif_document_id IS NOT NULL;

