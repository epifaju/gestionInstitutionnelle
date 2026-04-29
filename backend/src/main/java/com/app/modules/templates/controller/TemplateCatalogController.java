package com.app.modules.templates.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.templates.dto.TemplateAvailableResponse;
import com.app.modules.templates.entity.TemplateCategory;
import com.app.modules.templates.entity.TemplateDefinition;
import com.app.modules.templates.entity.TemplateFormat;
import com.app.modules.templates.entity.TemplateStatus;
import com.app.modules.templates.repository.TemplateDefinitionRepository;
import com.app.modules.templates.repository.TemplateRevisionRepository;
import com.app.modules.templates.service.TemplateOutputFormat;
import com.app.shared.dto.ApiResponse;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates/catalog")
@RequiredArgsConstructor
public class TemplateCatalogController {

    private final TemplateDefinitionRepository definitionRepository;
    private final TemplateRevisionRepository revisionRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TemplateAvailableResponse>>> listAvailable(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam String subjectType
    ) {
        TemplateCategory category = mapSubjectToCategory(subjectType);
        List<TemplateDefinition> defs = definitionRepository.findByOrganisationIdAndCategoryAndStatus(
                user.getOrganisationId(), category, TemplateStatus.ACTIVE
        );

        List<TemplateAvailableResponse> out = defs.stream().map(d -> {
            var rev = revisionRepository.findTopByTemplate_IdOrderByVersionDesc(d.getId()).orElse(null);
            return new TemplateAvailableResponse(
                    d.getCode(),
                    d.getLabel(),
                    d.getCategory(),
                    d.getFormat(),
                    allowedOutputs(d.getFormat()),
                    rev != null,
                    rev != null ? rev.getVersion() : null
            );
        }).toList();

        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    private static List<TemplateOutputFormat> allowedOutputs(TemplateFormat f) {
        if (f == TemplateFormat.HTML) {
            return List.of(TemplateOutputFormat.HTML);
        }
        return List.of(TemplateOutputFormat.PDF, TemplateOutputFormat.DOCX);
    }

    private static TemplateCategory mapSubjectToCategory(String raw) {
        if (raw == null) throw BusinessException.badRequest("TEMPLATE_SUBJECT_INVALIDE");
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case "MISSION" -> TemplateCategory.MISSION;
            case "FRAIS", "FRAISMISSION", "EXPENSE" -> TemplateCategory.FRAIS;
            case "CONTRAT" -> TemplateCategory.CONTRAT;
            case "COURRIER", "LETTER" -> TemplateCategory.COURRIER;
            case "PV" -> TemplateCategory.PV;
            default -> throw BusinessException.badRequest("TEMPLATE_SUBJECT_UNSUPPORTED");
        };
    }
}

