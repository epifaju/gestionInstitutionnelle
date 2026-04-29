package com.app.modules.templates.service;

import com.app.modules.missions.dto.MissionResponse;
import com.app.modules.missions.service.MissionService;
import com.app.modules.rh.dto.ContratResponse;
import com.app.modules.rh.service.ContratService;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateDataService {

    private final MissionService missionService;
    private final ContratService contratService;

    public Map<String, String> buildValues(String subjectType, UUID subjectId, UUID orgId) {
        if (subjectType == null) throw BusinessException.badRequest("TEMPLATE_SUBJECT_INVALIDE");
        String t = subjectType.trim();
        Map<String, String> out = new HashMap<>();
        if ("Mission".equalsIgnoreCase(t)) {
            MissionResponse m = missionService.getById(subjectId, orgId);
            out.put("mission.id", m.id().toString());
            out.put("mission.titre", nz(m.titre()));
            out.put("mission.destination", nz(m.destination()));
            out.put("mission.dateDepart", nz(m.dateDepart()));
            out.put("mission.dateRetour", nz(m.dateRetour()));
            out.put("mission.nbJours", String.valueOf(m.nbJours()));
            out.put("mission.statut", nz(m.statut()));
            out.put("mission.salarieNomComplet", nz(m.salarieNomComplet()));
            out.put("mission.soldeARegler", String.valueOf(m.soldeARegler()));
            out.put("mission.totalFraisValides", String.valueOf(m.totalFraisValides()));
            return out;
        }
        if ("Contrat".equalsIgnoreCase(t) || "CONTRAT".equalsIgnoreCase(t)) {
            ContratResponse c = contratService.getById(subjectId, orgId);
            out.put("contrat.id", c.id().toString());
            out.put("contrat.typeContrat", nz(c.typeContrat()));
            out.put("contrat.dateDebutContrat", nz(c.dateDebutContrat()));
            out.put("contrat.dateFinContrat", nz(c.dateFinContrat()));
            out.put("contrat.numeroContrat", nz(c.numeroContrat()));
            out.put("contrat.intitulePoste", nz(c.intitulePoste()));
            out.put("contrat.motifCdd", nz(c.motifCdd()));
            out.put("contrat.conventionCollective", nz(c.conventionCollective()));
            out.put("contrat.renouvellementNumero", nz(c.renouvellementNumero()));
            out.put("contrat.decisionFin", nz(c.decisionFin()));
            out.put("contrat.dateDecision", nz(c.dateDecision()));
            out.put("contrat.commentaireDecision", nz(c.commentaireDecision()));
            out.put("contrat.actif", String.valueOf(c.actif()));
            out.put("salarie.id", c.salarieId().toString());
            out.put("salarie.nomComplet", nz(c.salarieNomComplet()));
            out.put("salarie.matricule", nz(c.matricule()));
            out.put("salarie.service", nz(c.service()));
            return out;
        }
        if ("Courrier".equalsIgnoreCase(t) || "COURRIER".equalsIgnoreCase(t)) {
            // Generic letter templates can be created without placeholders (or using organisation/subject ids)
            out.put("courrier.organisationId", orgId.toString());
            out.put("courrier.subjectId", subjectId.toString());
            return out;
        }
        throw BusinessException.badRequest("TEMPLATE_SUBJECT_UNSUPPORTED");
    }

    private static String nz(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}

