-- Vue : réalisé dépenses (factures) et réalisé recettes (recettes) par ligne de budget
DROP VIEW IF EXISTS v_execution_budget;
CREATE VIEW v_execution_budget AS
SELECT lb.id AS ligne_id,
       lb.budget_id,
       lb.categorie_id,
       cd.libelle AS categorie_libelle,
       lb.type,
       lb.montant_prevu,
       COALESCE(
               CASE
                   WHEN lb.type = 'DEPENSE' THEN fd.montant
                   WHEN lb.type = 'RECETTE' THEN fr.montant
                   END,
               0
       ) AS montant_realise,
       lb.montant_prevu - COALESCE(
               CASE
                   WHEN lb.type = 'DEPENSE' THEN fd.montant
                   WHEN lb.type = 'RECETTE' THEN fr.montant
                   END,
               0
       ) AS ecart,
       CASE
           WHEN lb.montant_prevu > 0 THEN ROUND(
                   COALESCE(
                           CASE
                               WHEN lb.type = 'DEPENSE' THEN fd.montant
                               WHEN lb.type = 'RECETTE' THEN fr.montant
                               END,
                           0
                   ) / lb.montant_prevu * 100,
                   1
               )
           ELSE 0
           END AS taux_execution_pct
FROM lignes_budget lb
         JOIN categories_depenses cd ON cd.id = lb.categorie_id
         LEFT JOIN (
    SELECT categorie_id, SUM(montant_ttc * taux_change_eur) AS montant
    FROM factures
    WHERE statut IN ('A_PAYER', 'PAYE')
    GROUP BY categorie_id
) fd ON fd.categorie_id = lb.categorie_id AND lb.type = 'DEPENSE'
         LEFT JOIN (
    SELECT categorie_id, SUM(montant * taux_change_eur) AS montant
    FROM recettes
    GROUP BY categorie_id
) fr ON fr.categorie_id = lb.categorie_id AND lb.type = 'RECETTE';
