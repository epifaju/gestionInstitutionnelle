package com.app.modules.rh.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.dto.MarquerPayeRequest;
import com.app.modules.rh.dto.PaieResponse;
import com.app.modules.rh.entity.HistoriqueSalaire;
import com.app.modules.rh.entity.PaiementSalaire;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutPaie;
import com.app.modules.rh.entity.StatutSalarie;
import com.app.modules.rh.repository.HistoriqueSalaireRepository;
import com.app.modules.rh.repository.PaiementSalaireRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaieService {

    private final OrganisationRepository organisationRepository;
    private final SalarieRepository salarieRepository;
    private final HistoriqueSalaireRepository historiqueSalaireRepository;
    private final PaiementSalaireRepository paiementSalaireRepository;
    private final AuditLogService auditLogService;

    @Scheduled(cron = "0 0 1 1 * *")
    @Transactional
    public void creerLignesPaiementAnnuelles() {
        int annee = LocalDate.now().getYear();
        organisationRepository.findByActifTrue().forEach(o -> {
            UUID orgId = o.getId();
            List<Salarie> salaries = salarieRepository.findByOrganisationIdAndStatut(orgId, StatutSalarie.ACTIF);
            for (Salarie s : salaries) {
                HistoriqueSalaire h =
                        historiqueSalaireRepository
                                .findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(s.getId())
                                .orElseGet(
                                        () ->
                                                historiqueSalaireRepository
                                                        .findBySalarie_IdOrderByDateDebutDesc(s.getId())
                                                        .stream()
                                                        .findFirst()
                                                        .orElse(null));
                if (h == null) {
                    continue;
                }
                BigDecimal montant = h.getMontantNet();
                String devise = h.getDevise();
                for (int mois = 1; mois <= 12; mois++) {
                    if (paiementSalaireRepository.existsBySalarie_IdAndMoisAndAnnee(s.getId(), mois, annee)) {
                        continue;
                    }
                    PaiementSalaire p = new PaiementSalaire();
                    p.setOrganisationId(orgId);
                    p.setSalarie(s);
                    p.setMois(mois);
                    p.setAnnee(annee);
                    p.setMontant(montant);
                    p.setDevise(devise);
                    p.setStatut(StatutPaie.EN_ATTENTE);
                    paiementSalaireRepository.save(p);
                }
            }
        });
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN','FINANCIER')")
    @Transactional(readOnly = true)
    public Page<PaieResponse> getPaieAnnuelle(UUID salarieId, int annee, UUID orgId, Pageable pageable) {
        Salarie s = loadSalarieOwned(salarieId, orgId);
        return paiementSalaireRepository
                .findBySalarie_IdAndAnneeOrderByMoisAsc(s.getId(), annee, pageable)
                .map(this::toResponse);
    }

    /** Vue globale paie (tous salariés) pour une année — PRD navigation « Paie ». */
    @PreAuthorize("hasAnyRole('RH','ADMIN','FINANCIER')")
    @Transactional(readOnly = true)
    public Page<PaieResponse> listPaieOrganisation(UUID orgId, int annee, Pageable pageable) {
        return paiementSalaireRepository
                .findByOrganisationIdAndAnneeOrderBySalarie_NomAscSalarie_PrenomAscMoisAsc(orgId, annee, pageable)
                .map(this::toResponse);
    }

    @PreAuthorize("hasAnyRole('RH','FINANCIER')")
    @Transactional
    public PaieResponse marquerPaye(UUID id, MarquerPayeRequest req, UUID orgId) {
        UUID userId = currentUserId();
        PaiementSalaire p =
                paiementSalaireRepository.findById(id).orElseThrow(() -> BusinessException.notFound("PAIEMENT_ABSENT"));
        if (!p.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("PAIEMENT_ORG_MISMATCH");
        }
        if (p.getStatut() != StatutPaie.EN_ATTENTE) {
            throw BusinessException.badRequest("PAIEMENT_STATUT_INVALIDE");
        }
        Map<String, Object> avant = snapshotPaie(p);
        p.setStatut(StatutPaie.PAYE);
        p.setDatePaiement(req.datePaiement());
        p.setModePaiement(req.modePaiement());
        p.setNotes(req.notes());
        paiementSalaireRepository.save(p);
        auditLogService.logRh(orgId, userId, "UPDATE", "PaiementSalaire", p.getId(), avant, snapshotPaie(p));
        return toResponse(p);
    }

    private PaieResponse toResponse(PaiementSalaire p) {
        Salarie s = p.getSalarie();
        String nom = (s.getNom() + " " + s.getPrenom()).trim();
        return new PaieResponse(
                p.getId(),
                nom,
                s.getMatricule(),
                p.getMois(),
                p.getAnnee(),
                p.getMontant(),
                p.getDevise(),
                p.getDatePaiement(),
                p.getModePaiement(),
                p.getStatut().name());
    }

    private Map<String, Object> snapshotPaie(PaiementSalaire p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", p.getStatut() != null ? p.getStatut().name() : null);
        m.put("mois", p.getMois());
        m.put("annee", p.getAnnee());
        m.put("montant", p.getMontant());
        m.put("datePaiement", p.getDatePaiement());
        m.put("modePaiement", p.getModePaiement());
        return m;
    }

    private Salarie loadSalarieOwned(UUID id, UUID orgId) {
        Salarie s = salarieRepository.findById(id).orElseThrow(() -> BusinessException.notFound("SALARIE_ABSENT"));
        if (!s.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("SALARIE_ORG_MISMATCH");
        }
        return s;
    }

    private static UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails ud)) {
            throw BusinessException.unauthorized("TOKEN_INVALIDE");
        }
        return ud.getId();
    }
}
