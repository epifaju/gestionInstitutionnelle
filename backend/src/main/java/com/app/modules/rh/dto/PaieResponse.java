package com.app.modules.rh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaieResponse(
        UUID id,
        UUID salarieId,
        String salarieNomComplet,
        String matricule,
        int mois,
        int annee,
        BigDecimal montant,
        String devise,
        LocalDate datePaiement,
        String modePaiement,
        String statut,
        UUID bulletinId,
        boolean hasPayslip
) {
}
