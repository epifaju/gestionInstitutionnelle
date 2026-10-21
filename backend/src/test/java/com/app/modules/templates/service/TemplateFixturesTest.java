package com.app.modules.templates.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TemplateFixturesTest {

    @Test
    void courrierFixture_canBeRendered() throws Exception {
        TemplateRenderService svc = new TemplateRenderService();
        byte[] docx = Files.readAllBytes(Path.of("..", "templates", "courrier.docx"));
        Map<String, String> values = new HashMap<>();
        values.put("courrier.organisationId", "ORG");
        values.put("courrier.subjectId", "SUBJ");
        values.put("courrier.reference", "REF-1");
        values.put("courrier.sens", "SORTIE");
        values.put("courrier.statut", "ENREGISTRE");
        values.put("courrier.dateCourrier", "2026-04-01");
        values.put("courrier.canal", "EMAIL");
        values.put("courrier.objet", "Objet test");
        values.put("courrier.expediteur", "Expéditeur");
        values.put("courrier.destinataire", "Destinataire");
        values.put("courrier.notes", "Corps du courrier");
        assertDoesNotThrow(() -> svc.renderDocx(docx, values));
    }

    @Test
    void contratFixture_canBeRendered() throws Exception {
        TemplateRenderService svc = new TemplateRenderService();
        byte[] docx = Files.readAllBytes(Path.of("..", "templates", "contrat.docx"));
        Map<String, String> values = new HashMap<>();
        values.put("contrat.id", "CID");
        values.put("contrat.typeContrat", "CDD");
        values.put("contrat.numeroContrat", "N-1");
        values.put("contrat.intitulePoste", "Poste");
        values.put("contrat.conventionCollective", "CC");
        values.put("contrat.dateDebutContrat", "2026-01-01");
        values.put("contrat.dateFinContrat", "2026-12-31");
        values.put("contrat.dateFinPeriodeEssai", "2026-02-01");
        values.put("contrat.motifCdd", "Motif");
        values.put("contrat.renouvellementNumero", "1");
        values.put("contrat.decisionFin", "EN_ATTENTE");
        values.put("contrat.dateDecision", "");
        values.put("contrat.commentaireDecision", "");
        values.put("salarie.nomComplet", "Test User");
        values.put("salarie.matricule", "M01");
        values.put("salarie.service", "Service");
        assertDoesNotThrow(() -> svc.renderDocx(docx, values));
    }
}

