-- Allow longer category codes (UI generates e.g. DEPENSE_FONCTIONNEMENT).
ALTER TABLE categories_depenses
  ALTER COLUMN code TYPE VARCHAR(60);

