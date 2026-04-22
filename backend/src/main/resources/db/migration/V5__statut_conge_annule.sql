-- Annulation d'un congé validé (workflow métier §8.1)
ALTER TYPE statut_conge ADD VALUE IF NOT EXISTS 'ANNULE';
