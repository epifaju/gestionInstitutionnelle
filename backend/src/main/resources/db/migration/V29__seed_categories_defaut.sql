-- Catégories finance par défaut (nouvelle org / base vide).
-- Idempotent : n'insère que si le code n'existe pas déjà pour l'organisation.

INSERT INTO categories_depenses (organisation_id, libelle, code, type, couleur, actif)
SELECT o.id, v.libelle, v.code, v.type::type_categorie, v.couleur, TRUE
FROM organisations o
CROSS JOIN (VALUES
    ('Fonctionnement',     'DEPENSE_FONCTIONNEMENT', 'DEPENSE', '#64748B'),
    ('Personnel',          'DEPENSE_PERSONNEL',      'DEPENSE', '#0EA5E9'),
    ('Missions',           'DEPENSE_MISSIONS',       'DEPENSE', '#8B5CF6'),
    ('Équipement',         'DEPENSE_EQUIPEMENT',     'DEPENSE', '#F59E0B'),
    ('Autres dépenses',    'DEPENSE_AUTRES',         'DEPENSE', '#6B7280'),
    ('Frais de service',   'RECETTE_FRAIS_SERVICE',  'RECETTE', '#10B981'),
    ('Adhésion',           'RECETTE_ADHESION',       'RECETTE', '#14B8A6'),
    ('Don',                'RECETTE_DON',            'RECETTE', '#22C55E'),
    ('Subvention',         'RECETTE_SUBVENTION',     'RECETTE', '#84CC16'),
    ('Prestation',         'RECETTE_PRESTATION',     'RECETTE', '#06B6D4')
) AS v(libelle, code, type, couleur)
WHERE NOT EXISTS (
    SELECT 1
    FROM categories_depenses c
    WHERE c.organisation_id = o.id
      AND c.code = v.code
);
