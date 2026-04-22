package com.app.modules.rh.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.dto.CongeRequest;
import com.app.modules.rh.dto.CongeResponse;
import com.app.modules.rh.dto.CongeValidationRequest;
import com.app.modules.rh.dto.DroitsCongesDto;
import com.app.modules.rh.entity.CongeAbsence;
import com.app.modules.rh.entity.DroitsConges;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutConge;
import com.app.modules.rh.entity.TypeConge;
import com.app.modules.rh.repository.CongeRepository;
import com.app.modules.rh.repository.CongeSpecifications;
import com.app.modules.rh.repository.DroitsCongesRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.modules.rh.util.JoursOuvres;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CongeService {

    private final CongeRepository congeRepository;
    private final SalarieRepository salarieRepository;
    private final DroitsCongesRepository droitsCongesRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AuditLogService auditLogService;

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Page<CongeResponse> listConges(
            UUID orgId,
            String statut,
            String typeConge,
            LocalDate debut,
            LocalDate fin,
            String service,
            UUID salarieId,
            Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean rhOuAdmin = isRhOrAdmin(auth);
        if (!rhOuAdmin) {
            UUID userId = currentUserId();
            UUID ownSalarieId =
                    salarieRepository
                            .findByOrganisationIdAndUtilisateur_Id(orgId, userId)
                            .map(Salarie::getId)
                            .orElseThrow(() -> BusinessException.forbidden("CONGE_NON_AUTORISE"));
            if (salarieId != null && !salarieId.equals(ownSalarieId)) {
                throw BusinessException.forbidden("CONGE_NON_AUTORISE");
            }
            // Forcer le filtre salarié pour éviter toute fuite (EMPLOYE -> ses congés uniquement)
            salarieId = ownSalarieId;
            service = null;
        }

        StatutConge st = parseStatutConge(statut);
        TypeConge tc = parseTypeConge(typeConge);
        String svc = service == null || service.isBlank() ? null : service.trim();

        Specification<CongeAbsence> spec =
                Specification.where(CongeSpecifications.organisationId(orgId))
                        .and(CongeSpecifications.statutOptional(st))
                        .and(CongeSpecifications.typeCongeOptional(tc))
                        .and(CongeSpecifications.dateDebutFilter(debut))
                        .and(CongeSpecifications.dateFinFilter(fin))
                        .and(CongeSpecifications.salarieJoinFilters(svc, salarieId));

        Pageable sorted =
                pageable.getSort().isSorted()
                        ? pageable
                        : PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                Sort.by(Sort.Order.desc("createdAt")));

        return congeRepository.findAll(spec, sorted).map(this::toResponse);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public CongeResponse soumettre(CongeRequest req, boolean draft, UUID orgId, UUID auteurId) {
        UUID userId = currentUserId();
        if (!auteurId.equals(userId)) {
            throw BusinessException.unauthorized("TOKEN_INVALIDE");
        }
        Salarie s = salarieRepository.findById(req.salarieId()).orElseThrow(() -> BusinessException.notFound("SALARIE_ABSENT"));
        if (!s.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("SALARIE_ORG_MISMATCH");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean rhOuAdmin = isRhOrAdmin(auth);
        if (!rhOuAdmin) {
            UUID link = s.getUtilisateurId();
            if (link == null || !link.equals(auteurId)) {
                throw BusinessException.forbidden("CONGE_NON_AUTORISE");
            }
        }
        TypeConge type;
        try {
            type = TypeConge.valueOf(req.typeConge().trim().toUpperCase());
        } catch (Exception e) {
            throw BusinessException.badRequest("TYPE_CONGE_INVALIDE");
        }
        if (req.dateFin().isBefore(req.dateDebut())) {
            throw BusinessException.badRequest("DATES_CONGE_INVALIDES");
        }
        BigDecimal nb = JoursOuvres.compter(req.dateDebut(), req.dateFin());
        if (!draft) {
            // Soumission immédiate -> contrôles au dépôt
            if (consommeSolde(type)) {
                int annee = req.dateDebut().getYear();
                DroitsConges droits =
                        droitsCongesRepository
                                .findBySalarie_IdAndAnnee(s.getId(), annee)
                                .orElseThrow(() -> BusinessException.notFound("DROITS_CONGES_ABSENTS"));
                if (droits.getJoursRestants().compareTo(nb) < 0) {
                    throw BusinessException.badRequest("CONGE_SOLDE_INSUFFISANT");
                }
            }
            if (congeRepository.existsChevauchement(s.getId(), req.dateDebut(), req.dateFin(), StatutConge.VALIDE)) {
                throw BusinessException.badRequest("CONGE_CHEVAUCHEMENT");
            }
        }

        CongeAbsence c = new CongeAbsence();
        c.setOrganisationId(orgId);
        c.setSalarie(s);
        c.setTypeConge(type);
        c.setDateDebut(req.dateDebut());
        c.setDateFin(req.dateFin());
        c.setNbJours(nb);
        c.setStatut(draft ? StatutConge.BROUILLON : StatutConge.EN_ATTENTE);
        c.setCommentaire(req.commentaire());
        c = congeRepository.save(c);
        auditLogService.logRh(orgId, auteurId, "CREATE", "CongeAbsence", c.getId(), null, snapshotConge(c));
        return toResponse(c);
    }

    /**
     * Soumission d'un brouillon : BROUILLON -> EN_ATTENTE (PRD §8.1).
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public CongeResponse soumettreBrouillon(UUID congeId, UUID orgId, UUID auteurId) {
        UUID userId = currentUserId();
        if (!auteurId.equals(userId)) {
            throw BusinessException.unauthorized("TOKEN_INVALIDE");
        }
        CongeAbsence c = loadOwned(congeId, orgId);
        if (c.getStatut() != StatutConge.BROUILLON) {
            throw BusinessException.badRequest("CONGE_STATUT_INVALIDE");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean rhOuAdmin = isRhOrAdmin(auth);
        if (!rhOuAdmin) {
            UUID link = c.getSalarie().getUtilisateurId();
            if (link == null || !link.equals(auteurId)) {
                throw BusinessException.forbidden("CONGE_NON_AUTORISE");
            }
        }

        // Contrôles avant soumission
        if (consommeSolde(c.getTypeConge())) {
            int annee = c.getDateDebut().getYear();
            DroitsConges droits =
                    droitsCongesRepository
                            .findBySalarie_IdAndAnnee(c.getSalarie().getId(), annee)
                            .orElseThrow(() -> BusinessException.notFound("DROITS_CONGES_ABSENTS"));
            if (droits.getJoursRestants().compareTo(c.getNbJours()) < 0) {
                throw BusinessException.badRequest("CONGE_SOLDE_INSUFFISANT");
            }
        }
        if (congeRepository.existsChevauchement(c.getSalarie().getId(), c.getDateDebut(), c.getDateFin(), StatutConge.VALIDE)) {
            throw BusinessException.badRequest("CONGE_CHEVAUCHEMENT");
        }

        Map<String, Object> avant = snapshotConge(c);
        c.setStatut(StatutConge.EN_ATTENTE);
        congeRepository.save(c);
        auditLogService.logRh(orgId, auteurId, "UPDATE", "CongeAbsence", c.getId(), avant, snapshotConge(c));
        return toResponse(c);
    }

    @PreAuthorize("hasRole('RH')")
    @Transactional
    public CongeResponse valider(UUID id, UUID valideurId, UUID orgId) {
        UUID userId = currentUserId();
        if (!valideurId.equals(userId)) {
            throw BusinessException.unauthorized("TOKEN_INVALIDE");
        }
        CongeAbsence c = loadOwned(id, orgId);
        if (c.getStatut() != StatutConge.EN_ATTENTE) {
            throw BusinessException.badRequest("CONGE_STATUT_INVALIDE");
        }
        Map<String, Object> avant = snapshotConge(c);
        int annee = c.getDateDebut().getYear();
        DroitsConges droits =
                droitsCongesRepository
                        .findBySalarie_IdAndAnnee(c.getSalarie().getId(), annee)
                        .orElseThrow(() -> BusinessException.notFound("DROITS_CONGES_ABSENTS"));
        if (consommeSolde(c.getTypeConge())) {
            if (droits.getJoursRestants().compareTo(c.getNbJours()) < 0) {
                throw BusinessException.badRequest("CONGE_SOLDE_INSUFFISANT");
            }
            droits.setJoursPris(droits.getJoursPris().add(c.getNbJours()));
            droits.setJoursRestants(droits.getJoursRestants().subtract(c.getNbJours()));
            droitsCongesRepository.save(droits);
        }
        c.setStatut(StatutConge.VALIDE);
        c.setValideur(utilisateurRepository.getReferenceById(valideurId));
        c.setDateValidation(Instant.now());
        c.setMotifRejet(null);
        congeRepository.save(c);
        auditLogService.logRh(orgId, userId, "UPDATE", "CongeAbsence", c.getId(), avant, snapshotConge(c));
        return toResponse(c);
    }

    @PreAuthorize("hasRole('RH')")
    @Transactional
    public CongeResponse rejeter(UUID id, CongeValidationRequest req, UUID orgId) {
        UUID userId = currentUserId();
        CongeAbsence c = loadOwned(id, orgId);
        if (c.getStatut() != StatutConge.EN_ATTENTE) {
            throw BusinessException.badRequest("CONGE_STATUT_INVALIDE");
        }
        Map<String, Object> avant = snapshotConge(c);
        c.setStatut(StatutConge.REJETE);
        c.setMotifRejet(req.motifRejet());
        c.setValideur(utilisateurRepository.getReferenceById(userId));
        c.setDateValidation(Instant.now());
        congeRepository.save(c);
        auditLogService.logRh(orgId, userId, "UPDATE", "CongeAbsence", c.getId(), avant, snapshotConge(c));
        return toResponse(c);
    }

    /**
     * Annulation d'un congé déjà validé : restauration du solde si besoin. Uniquement si la date de début est
     * strictement postérieure à aujourd'hui (PRD §8.1).
     */
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    @Transactional
    public CongeResponse annulerValide(UUID id, UUID orgId) {
        UUID userId = currentUserId();
        CongeAbsence c = loadOwned(id, orgId);
        if (c.getStatut() != StatutConge.VALIDE) {
            throw BusinessException.badRequest("CONGE_STATUT_INVALIDE");
        }
        LocalDate today = LocalDate.now();
        if (!c.getDateDebut().isAfter(today)) {
            throw BusinessException.badRequest("CONGE_ANNULATION_DATE");
        }
        Map<String, Object> avant = snapshotConge(c);
        if (consommeSolde(c.getTypeConge())) {
            int annee = c.getDateDebut().getYear();
            DroitsConges droits =
                    droitsCongesRepository
                            .findBySalarie_IdAndAnnee(c.getSalarie().getId(), annee)
                            .orElseThrow(() -> BusinessException.notFound("DROITS_CONGES_ABSENTS"));
            droits.setJoursPris(droits.getJoursPris().subtract(c.getNbJours()));
            droits.setJoursRestants(droits.getJoursRestants().add(c.getNbJours()));
            droitsCongesRepository.save(droits);
        }
        c.setStatut(StatutConge.ANNULE);
        c.setMotifRejet(null);
        congeRepository.save(c);
        auditLogService.logRh(orgId, userId, "UPDATE", "CongeAbsence", c.getId(), avant, snapshotConge(c));
        return toResponse(c);
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    @Transactional(readOnly = true)
    public List<CongeResponse> getCalendrier(UUID orgId, LocalDate debut, LocalDate fin) {
        return congeRepository.findCalendrier(orgId, debut, fin).stream().map(this::toResponse).toList();
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN') or @securityService.isSelf(#salarieId, authentication)")
    @Transactional(readOnly = true)
    public DroitsCongesDto getDroits(UUID salarieId, int annee, UUID orgId) {
        Salarie s = loadSalarieOwned(salarieId, orgId);
        return droitsCongesRepository
                .findBySalarie_IdAndAnnee(s.getId(), annee)
                .map(
                        d ->
                                new DroitsCongesDto(
                                        d.getAnnee(),
                                        d.getJoursDroit(),
                                        d.getJoursPris(),
                                        d.getJoursRestants()))
                .orElseThrow(() -> BusinessException.notFound("DROITS_CONGES_ABSENTS"));
    }

    private CongeAbsence loadOwned(UUID id, UUID orgId) {
        CongeAbsence c = congeRepository.findById(id).orElseThrow(() -> BusinessException.notFound("CONGE_ABSENT"));
        if (!c.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("CONGE_ORG_MISMATCH");
        }
        return c;
    }

    private Salarie loadSalarieOwned(UUID id, UUID orgId) {
        Salarie s = salarieRepository.findById(id).orElseThrow(() -> BusinessException.notFound("SALARIE_ABSENT"));
        if (!s.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("SALARIE_ORG_MISMATCH");
        }
        return s;
    }

    private CongeResponse toResponse(CongeAbsence c) {
        Salarie sal = c.getSalarie();
        String nomComplet = (sal.getNom() + " " + sal.getPrenom()).trim();
        String valideurNc = null;
        if (c.getValideur() != null) {
            Utilisateur v = c.getValideur();
            valideurNc = (v.getNom() + " " + v.getPrenom()).trim();
        }
        LocalDateTime dtVal =
                c.getDateValidation() == null
                        ? null
                        : LocalDateTime.ofInstant(c.getDateValidation(), ZoneId.systemDefault());
        LocalDateTime created =
                c.getCreatedAt() == null
                        ? null
                        : LocalDateTime.ofInstant(c.getCreatedAt(), ZoneId.systemDefault());
        return new CongeResponse(
                c.getId(),
                sal.getId(),
                nomComplet,
                sal.getService(),
                c.getTypeConge().name(),
                c.getDateDebut(),
                c.getDateFin(),
                c.getNbJours(),
                c.getStatut().name(),
                valideurNc,
                dtVal,
                c.getMotifRejet(),
                c.getCommentaire(),
                created);
    }

    private Map<String, Object> snapshotConge(CongeAbsence c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", c.getStatut() != null ? c.getStatut().name() : null);
        m.put("typeConge", c.getTypeConge() != null ? c.getTypeConge().name() : null);
        m.put("dateDebut", c.getDateDebut());
        m.put("dateFin", c.getDateFin());
        m.put("nbJours", c.getNbJours());
        m.put("motifRejet", c.getMotifRejet());
        return m;
    }

    private static boolean consommeSolde(TypeConge t) {
        return t == TypeConge.ANNUEL || t == TypeConge.EXCEPTIONNEL;
    }

    private static StatutConge parseStatutConge(String statut) {
        if (statut == null || statut.isBlank()) {
            return null;
        }
        try {
            return StatutConge.valueOf(statut.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static TypeConge parseTypeConge(String typeConge) {
        if (typeConge == null || typeConge.isBlank()) {
            return null;
        }
        try {
            return TypeConge.valueOf(typeConge.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails ud)) {
            throw BusinessException.unauthorized("TOKEN_INVALIDE");
        }
        return ud.getId();
    }

    private static boolean isRhOrAdmin(Authentication auth) {
        if (auth == null) {
            return false;
        }
        for (var a : auth.getAuthorities()) {
            String r = a.getAuthority();
            if ("ROLE_RH".equals(r) || "ROLE_ADMIN".equals(r)) {
                return true;
            }
        }
        return false;
    }
}
