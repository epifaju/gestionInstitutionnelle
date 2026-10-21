package com.app.modules.courriers.service;

import com.app.modules.courriers.dto.CourrierRequest;
import com.app.modules.courriers.dto.CourrierResponse;
import com.app.modules.courriers.entity.CanalCourrier;
import com.app.modules.courriers.entity.Courrier;
import com.app.modules.courriers.entity.SensCourrier;
import com.app.modules.courriers.entity.StatutCourrier;
import com.app.modules.courriers.repository.CourrierRepository;
import com.app.modules.courriers.repository.CourrierSpecifications;
import com.app.modules.ged.entity.Document;
import com.app.modules.ged.repository.DocumentRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourrierService {

    private final CourrierRepository courrierRepository;
    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public Page<CourrierResponse> list(
            UUID orgId,
            String sens,
            String statut,
            String q,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        Specification<Courrier> spec = Specification
                .where(CourrierSpecifications.organisationId(orgId))
                .and(CourrierSpecifications.sensOptional(parseSensOptional(sens)))
                .and(CourrierSpecifications.statutOptional(parseStatutOptional(statut)))
                .and(CourrierSpecifications.queryOptional(q))
                .and(CourrierSpecifications.dateFromOptional(from))
                .and(CourrierSpecifications.dateToOptional(to));
        return courrierRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CourrierResponse getById(UUID id, UUID orgId) {
        return toResponse(require(id, orgId));
    }

    @Transactional
    public CourrierResponse create(CourrierRequest req, UUID orgId, UUID userId) {
        Courrier c = new Courrier();
        c.setOrganisationId(orgId);
        c.setCreatedBy(userId);
        applyRequest(c, req, orgId, true);
        if (c.getStatut() == null) {
            c.setStatut(StatutCourrier.BROUILLON);
        }
        return toResponse(courrierRepository.save(c));
    }

    @Transactional
    public CourrierResponse update(UUID id, CourrierRequest req, UUID orgId, boolean isAdmin) {
        Courrier c = require(id, orgId);
        assertEditable(c, isAdmin);
        applyRequest(c, req, orgId, false);
        return toResponse(courrierRepository.save(c));
    }

    @Transactional
    public CourrierResponse changerStatut(UUID id, String statutRaw, UUID orgId, boolean isAdmin) {
        Courrier c = require(id, orgId);
        assertEditable(c, isAdmin);
        StatutCourrier statut = parseStatutRequired(statutRaw);
        c.setStatut(statut);
        return toResponse(courrierRepository.save(c));
    }

    @Transactional
    public CourrierResponse rattacherDocument(UUID id, UUID documentId, UUID orgId, boolean isAdmin) {
        Courrier c = require(id, orgId);
        assertEditable(c, isAdmin);
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> BusinessException.notFound("DOCUMENT_NON_TROUVE"));
        if (!orgId.equals(doc.getOrganisationId()) || doc.isSupprime()) {
            throw BusinessException.notFound("DOCUMENT_NON_TROUVE");
        }
        c.setDocumentId(documentId);
        doc.setEntiteLieeType("COURRIER_REGISTRE");
        doc.setEntiteLieeId(c.getId());
        documentRepository.save(doc);
        return toResponse(courrierRepository.save(c));
    }

    private void applyRequest(Courrier c, CourrierRequest req, UUID orgId, boolean creating) {
        SensCourrier sens = parseSensRequired(req.sens());
        CanalCourrier canal = parseCanal(req.canal());
        c.setSens(sens);
        c.setObjet(req.objet().trim());
        c.setExpediteur(blankToNull(req.expediteur()));
        c.setDestinataire(blankToNull(req.destinataire()));
        c.setDateCourrier(req.dateCourrier());
        c.setDateReception(req.dateReception());
        c.setDateEnvoi(req.dateEnvoi());
        c.setCanal(canal);
        c.setNotes(blankToNull(req.notes()));

        if (StringUtils.hasText(req.statut())) {
            c.setStatut(parseStatutRequired(req.statut()));
        } else if (creating) {
            c.setStatut(StatutCourrier.BROUILLON);
        }

        String ref = blankToNull(req.reference());
        if (ref != null) {
            boolean dup = creating
                    ? courrierRepository.existsByOrganisationIdAndReferenceIgnoreCase(orgId, ref)
                    : courrierRepository.existsByOrganisationIdAndReferenceIgnoreCaseAndIdNot(orgId, ref, c.getId());
            if (dup) {
                throw BusinessException.badRequest("COURRIER_REFERENCE_DUPLIQUEE");
            }
        }
        c.setReference(ref);

        if (req.documentId() != null) {
            Document doc = documentRepository.findById(req.documentId())
                    .orElseThrow(() -> BusinessException.notFound("DOCUMENT_NON_TROUVE"));
            if (!orgId.equals(doc.getOrganisationId()) || doc.isSupprime()) {
                throw BusinessException.notFound("DOCUMENT_NON_TROUVE");
            }
            c.setDocumentId(req.documentId());
        }
    }

    private void assertEditable(Courrier c, boolean isAdmin) {
        if (c.getStatut() == StatutCourrier.ANNULE || c.getStatut() == StatutCourrier.CLASSE) {
            if (!isAdmin) {
                throw BusinessException.forbidden("COURRIER_VERROUILLE");
            }
        }
    }

    private Courrier require(UUID id, UUID orgId) {
        return courrierRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> BusinessException.notFound("COURRIER_NON_TROUVE"));
    }

    private SensCourrier parseSensRequired(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw BusinessException.badRequest("COURRIER_SENS_INVALIDE");
        }
        try {
            return SensCourrier.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("COURRIER_SENS_INVALIDE");
        }
    }

    private SensCourrier parseSensOptional(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return SensCourrier.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private StatutCourrier parseStatutRequired(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw BusinessException.badRequest("COURRIER_STATUT_INVALIDE");
        }
        try {
            return StatutCourrier.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("COURRIER_STATUT_INVALIDE");
        }
    }

    private StatutCourrier parseStatutOptional(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return StatutCourrier.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private CanalCourrier parseCanal(String raw) {
        if (!StringUtils.hasText(raw)) {
            return CanalCourrier.COURRIER_POSTAL;
        }
        try {
            return CanalCourrier.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("COURRIER_CANAL_INVALIDE");
        }
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private CourrierResponse toResponse(Courrier c) {
        return new CourrierResponse(
                c.getId(),
                c.getSens() != null ? c.getSens().name() : null,
                c.getStatut() != null ? c.getStatut().name() : null,
                c.getReference(),
                c.getObjet(),
                c.getExpediteur(),
                c.getDestinataire(),
                c.getDateCourrier(),
                c.getDateReception(),
                c.getDateEnvoi(),
                c.getCanal() != null ? c.getCanal().name() : null,
                c.getNotes(),
                c.getDocumentId(),
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
