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
import java.util.ArrayList;
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
                ensureLignesAnnee(s, annee, orgId);
            }
        });
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN','FINANCIER')")
    @Transactional
    public Page<PaieResponse> getPaieAnnuelle(UUID salarieId, int annee, UUID orgId, Pageable pageable) {
        Salarie s = loadSalarieOwned(salarieId, orgId);
        ensureLignesAnnee(s, annee, orgId);
        LocalDate embauche = s.getDateEmbauche();
        int startMonth = (embauche != null && embauche.getYear() == annee) ? embauche.getMonthValue() : 1;
        return paiementSalaireRepository
                .findBySalarie_IdAndAnneeAndMoisGreaterThanEqualOrderByMoisAsc(s.getId(), annee, startMonth, pageable)
                .map(this::toResponse);
    }

    /**
     * Garantit l'existence des 12 lignes de paie pour un salarié/année.
     * Utile pour les salariés activés après le job du 1er janvier (sinon l'UI est vide).
     */
    private void ensureLignesAnnee(Salarie s, int annee, UUID orgId) {
        // Ne créer des lignes qu'à partir du mois d'embauche
        LocalDate embauche = s.getDateEmbauche();
        if (embauche == null) return;
        if (embauche.getYear() > annee) return;
        int startMonth = embauche.getYear() == annee ? embauche.getMonthValue() : 1;

        // Nettoyage sûr: si des lignes ont été créées avant embauche, on les retire seulement
        // si elles ne sont pas payées (EN_ATTENTE et sans date de paiement).
        if (startMonth > 1) {
            paiementSalaireRepository.deleteBySalarie_IdAndAnneeAndMoisLessThanAndStatutAndDatePaiementIsNull(
                    s.getId(), annee, startMonth, StatutPaie.EN_ATTENTE);
        }

        List<PaiementSalaire> existing = paiementSalaireRepository.findBySalarie_IdAndAnneeOrderByMoisAsc(s.getId(), annee);

        List<HistoriqueSalaire> hist = historiqueSalaireRepository.findBySalarie_IdOrderByDateDebutDesc(s.getId());
        if (hist == null || hist.isEmpty()) return;

        boolean[] present = new boolean[13];
        for (PaiementSalaire p : existing) {
            Integer m = p.getMois();
            if (m != null && m >= 1 && m <= 12) present[m] = true;
        }

        List<PaiementSalaire> toCreate = new ArrayList<>();
        for (int mois = startMonth; mois <= 12; mois++) {
            if (present[mois]) continue;
            if (paiementSalaireRepository.existsBySalarie_IdAndMoisAndAnnee(s.getId(), mois, annee)) continue;

            LocalDate monthStart = LocalDate.of(annee, mois, 1);
            // Sur le mois d'embauche (si embauche après le 1er), on prend la grille active à la date d'embauche
            LocalDate refDate =
                    (embauche.getYear() == annee && embauche.getMonthValue() == mois && embauche.isAfter(monthStart))
                            ? embauche
                            : monthStart;
            HistoriqueSalaire grille = grilleActiveAu(refDate, hist);
            if (grille == null) continue;

            PaiementSalaire p = new PaiementSalaire();
            p.setOrganisationId(orgId);
            p.setSalarie(s);
            p.setMois(mois);
            p.setAnnee(annee);
            p.setMontant(grille.getMontantNet());
            p.setDevise(grille.getDevise());
            p.setStatut(StatutPaie.EN_ATTENTE);
            toCreate.add(p);
        }
        if (!toCreate.isEmpty()) {
            paiementSalaireRepository.saveAll(toCreate);
        }
    }

    private static HistoriqueSalaire grilleActiveAu(LocalDate date, List<HistoriqueSalaire> histDesc) {
        for (HistoriqueSalaire h : histDesc) {
            if (h.getDateDebut() == null) continue;
            if (h.getDateDebut().isAfter(date)) continue;
            LocalDate fin = h.getDateFin();
            if (fin == null || !fin.isBefore(date)) {
                return h;
            }
        }
        return null;
    }

    /**
     * Vue globale paie (tous salariés) pour une année — PRD navigation « Paie ».
     */
    @PreAuthorize("hasAnyRole('RH','ADMIN','FINANCIER')")
    @Transactional(readOnly = true)
    public Page<PaieResponse> listPaieOrganisation(UUID orgId, int annee, Pageable pageable) {
        return paiementSalaireRepository
                .findByOrganisationIdAndAnneeOrderBySalarie_NomAscSalarie_PrenomAscMoisAsc(orgId, annee, pageable)
                .map(this::toResponse);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Page<PaieResponse> listMyPaie(UUID orgId, UUID userId, int annee, Pageable pageable) {
        Salarie s = salarieRepository
                .findByOrganisationIdAndUtilisateur_Id(orgId, userId)
                .orElseGet(() -> {
                    String email = null;
                    try {
                        var u = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                        if (u != null && u.getPrincipal() instanceof CustomUserDetails ud) {
                            email = ud.getUsername();
                        }
                    } catch (Exception ignored) {
                        // noop
                    }
                    if (email == null || email.isBlank()) return null;
                    return salarieRepository.findByOrganisationIdAndEmailIgnoreCase(orgId, email.trim()).orElse(null);
                });
        if (s == null) {
            return Page.empty(pageable);
        }
        ensureLignesAnnee(s, annee, orgId);
        LocalDate embauche = s.getDateEmbauche();
        int startMonth = (embauche != null && embauche.getYear() == annee) ? embauche.getMonthValue() : 1;
        return paiementSalaireRepository
                .findBySalarie_IdAndAnneeAndMoisGreaterThanEqualOrderByMoisAsc(s.getId(), annee, startMonth, pageable)
                .map(this::toResponse);
    }

    @PreAuthorize("hasAnyRole('RH','FINANCIER','ADMIN')")
    @Transactional
    public PaieResponse marquerPaye(UUID id, MarquerPayeRequest req, UUID orgId) {
        UUID userId = currentUserId();
        PaiementSalaire p = paiementSalaireRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("PAIEMENT_ABSENT"));
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

    @PreAuthorize("hasAnyRole('RH','FINANCIER','ADMIN')")
    @Transactional
    public PaieResponse annuler(UUID id, UUID orgId) {
        UUID userId = currentUserId();
        PaiementSalaire p = paiementSalaireRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("PAIEMENT_ABSENT"));
        if (!p.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("PAIEMENT_ORG_MISMATCH");
        }
        if (p.getStatut() != StatutPaie.EN_ATTENTE) {
            throw BusinessException.badRequest("PAIEMENT_STATUT_INVALIDE");
        }
        Map<String, Object> avant = snapshotPaie(p);
        p.setStatut(StatutPaie.ANNULE);
        p.setDatePaiement(null);
        p.setModePaiement(null);
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
