package com.app.modules.rapports.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ExportJournalAuditRequest(
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin,
        String entite,
        String action,
        UUID utilisateurId
) {}

