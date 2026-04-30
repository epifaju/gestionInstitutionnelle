-- ============================================================
-- Todo Dashboard (views only) - V27
-- ============================================================

-- Drop existing views to allow re-run safely.
DROP VIEW IF EXISTS v_todo_rh;
DROP VIEW IF EXISTS v_todo_financier;
DROP VIEW IF EXISTS v_todo_admin;

-- ============================================================
-- VUE : COMPTAGES POUR RH
-- ============================================================
CREATE VIEW v_todo_rh AS
SELECT
    organisation_id,
    -- Congés
    COUNT(*) FILTER (
        WHERE source = 'CONGE' AND statut = 'EN_ATTENTE'
    ) AS conges_a_valider,
    -- Congés urgents (demande > 5 jours sans réponse)
    COUNT(*) FILTER (
        WHERE source = 'CONGE' AND statut = 'EN_ATTENTE'
        AND created_at < NOW() - INTERVAL '5 days'
    ) AS conges_urgents,
    -- Salaires en attente du mois courant
    COUNT(*) FILTER (
        WHERE source = 'PAIE' AND statut = 'EN_ATTENTE'
        AND mois = EXTRACT(MONTH FROM CURRENT_DATE)
        AND annee = EXTRACT(YEAR FROM CURRENT_DATE)
    ) AS salaires_mois_courant,
    -- Missions soumises (si table existe — géré en Java avec fallback)
    0 AS missions_a_approuver  -- placeholder, calculé en Java
FROM (
    -- Congés
    SELECT organisation_id, 'CONGE' AS source, statut::TEXT, created_at,
           NULL::INTEGER AS mois, NULL::INTEGER AS annee
    FROM conges_absences
    WHERE statut = 'EN_ATTENTE'
    UNION ALL
    -- Paie
    SELECT organisation_id, 'PAIE' AS source, statut::TEXT, created_at, mois, annee
    FROM paiements_salaires
    WHERE statut = 'EN_ATTENTE'
) AS combined
GROUP BY organisation_id;

-- ============================================================
-- VUE : COMPTAGES POUR FINANCIER
-- ============================================================
CREATE VIEW v_todo_financier AS
SELECT
    f.organisation_id,
    -- Factures à payer
    COUNT(f.id) FILTER (WHERE f.statut = 'A_PAYER')
        AS factures_a_payer,
    -- Factures en retard (A_PAYER depuis > 30 jours)
    COUNT(f.id) FILTER (
        WHERE f.statut = 'A_PAYER'
        AND f.date_facture < CURRENT_DATE - INTERVAL '30 days'
    ) AS factures_en_retard,
    -- Montant total des factures A_PAYER en EUR
    COALESCE(SUM(f.montant_ttc * f.taux_change_eur)
        FILTER (WHERE f.statut = 'A_PAYER'), 0)
        AS montant_total_a_payer,
    -- Salaires non payés mois courant
    COUNT(ps.id) FILTER (
        WHERE ps.statut = 'EN_ATTENTE'
        AND ps.mois = EXTRACT(MONTH FROM CURRENT_DATE)
        AND ps.annee = EXTRACT(YEAR FROM CURRENT_DATE)
    ) AS salaires_a_verser,
    -- Montant total salaires en attente ce mois
    COALESCE(SUM(ps.montant)
        FILTER (WHERE ps.statut = 'EN_ATTENTE'
        AND ps.mois = EXTRACT(MONTH FROM CURRENT_DATE)
        AND ps.annee = EXTRACT(YEAR FROM CURRENT_DATE)), 0)
        AS montant_salaires_a_verser
FROM factures f
LEFT JOIN paiements_salaires ps ON ps.organisation_id = f.organisation_id
GROUP BY f.organisation_id;

-- ============================================================
-- VUE : COMPTAGES POUR ADMIN
-- ============================================================
CREATE VIEW v_todo_admin AS
SELECT
    organisation_id,
    -- Factures en retard critique (> 45 jours)
    COUNT(*) FILTER (
        WHERE source = 'FACTURE_RETARD'
    ) AS factures_retard_critique,
    -- Budget BROUILLON à valider
    COUNT(*) FILTER (
        WHERE source = 'BUDGET_BROUILLON'
    ) AS budgets_a_valider,
    -- Biens matériels défaillants
    COUNT(*) FILTER (
        WHERE source = 'BIEN_DEFAILLANT'
    ) AS biens_defaillants
FROM (
    -- Factures en retard critique
    SELECT organisation_id, 'FACTURE_RETARD' AS source
    FROM factures
    WHERE statut = 'A_PAYER'
      AND date_facture < CURRENT_DATE - INTERVAL '45 days'
    UNION ALL
    -- Budgets BROUILLON
    SELECT organisation_id, 'BUDGET_BROUILLON' AS source
    FROM budgets_annuels
    WHERE statut = 'BROUILLON'
      AND annee = EXTRACT(YEAR FROM CURRENT_DATE)
    UNION ALL
    -- Biens défaillants
    SELECT organisation_id, 'BIEN_DEFAILLANT' AS source
    FROM biens_materiels
    WHERE etat = 'DEFAILLANT'
) AS combined
GROUP BY organisation_id;

-- Index pour accélérer les comptages (si pas déjà existants)
CREATE INDEX IF NOT EXISTS idx_conges_org_statut_created
    ON conges_absences(organisation_id, statut, created_at);
CREATE INDEX IF NOT EXISTS idx_factures_org_statut_date
    ON factures(organisation_id, statut, date_facture);
CREATE INDEX IF NOT EXISTS idx_paie_org_statut_mois
    ON paiements_salaires(organisation_id, statut, annee, mois);
CREATE INDEX IF NOT EXISTS idx_biens_org_etat
    ON biens_materiels(organisation_id, etat);

