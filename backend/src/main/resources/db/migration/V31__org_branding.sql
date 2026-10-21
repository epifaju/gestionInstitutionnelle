-- Identité affichée dans la barre latérale (titre + slogan), distincte du nom légal.
ALTER TABLE organisations
    ADD COLUMN IF NOT EXISTS titre_app VARCHAR(120),
    ADD COLUMN IF NOT EXISTS slogan VARCHAR(200);
