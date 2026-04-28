package com.app.modules.templates.service;

import com.app.modules.missions.dto.MissionResponse;
import com.app.modules.missions.service.MissionService;
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
        throw BusinessException.badRequest("TEMPLATE_SUBJECT_UNSUPPORTED");
    }

    private static String nz(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}

