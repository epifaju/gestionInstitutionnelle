package com.app.modules.rapports.dto;

public record ConfigExportRequest(
        String piedPageMention,
        String couleurPrincipale,
        Integer seuilLignesSyncPdf,
        Integer seuilLignesSyncExcel,
        Boolean watermarkActif,
        String watermarkTexte
) {}

