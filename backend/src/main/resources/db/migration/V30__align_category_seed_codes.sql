-- Aligne les codes seed V29 sur les clés i18n frontend (Finance.categoryLabels.byCode).

UPDATE categories_depenses SET code = 'FONCT',  libelle = 'Fonctionnement'       WHERE code = 'DEPENSE_FONCTIONNEMENT';
UPDATE categories_depenses SET code = 'SAL',    libelle = 'Salaires'             WHERE code = 'DEPENSE_PERSONNEL';
UPDATE categories_depenses SET code = 'MISSION', libelle = 'Missions'            WHERE code = 'DEPENSE_MISSIONS';
UPDATE categories_depenses SET code = 'EQUIP',  libelle = 'Équipement'           WHERE code = 'DEPENSE_EQUIPEMENT';
UPDATE categories_depenses SET code = 'AUTRES', libelle = 'Autres dépenses'      WHERE code = 'DEPENSE_AUTRES';
UPDATE categories_depenses SET code = 'FRSRV',  libelle = 'Frais de service'     WHERE code = 'RECETTE_FRAIS_SERVICE';
UPDATE categories_depenses SET code = 'ADHES',  libelle = 'Adhésions'            WHERE code = 'RECETTE_ADHESION';
UPDATE categories_depenses SET code = 'DONS',   libelle = 'Dons des organisations' WHERE code = 'RECETTE_DON';
UPDATE categories_depenses SET code = 'SUBV',   libelle = 'Subventions'          WHERE code = 'RECETTE_SUBVENTION';
UPDATE categories_depenses SET code = 'PREST',  libelle = 'Prestation'           WHERE code = 'RECETTE_PRESTATION';
