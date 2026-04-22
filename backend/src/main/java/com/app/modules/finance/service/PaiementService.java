package com.app.modules.finance.service;

import com.app.audit.AuditLogService;
import com.app.modules.finance.dto.PaiementLigneRequest;
import com.app.modules.finance.dto.PaiementRequest;
import com.app.modules.finance.dto.PaiementResponse;
import com.app.modules.finance.entity.Facture;
import com.app.modules.finance.entity.FacturePaiement;
import com.app.modules.finance.entity.Paiement;
import com.app.modules.finance.entity.StatutFacture;
import com.app.modules.finance.repository.FacturePaiementRepository;
import com.app.modules.finance.repository.FactureRepository;
import com.app.modules.finance.repository.PaiementRepository;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaiementService {

    private final PaiementRepository paiementRepository;
    private final FactureRepository factureRepository;
    private final FacturePaiementRepository facturePaiementRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final FactureService factureService;
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    @Transactional(readOnly = true)
    public Page<PaiementResponse> list(UUID orgId, Pageable pageable) {
        return paiementRepository.findByOrganisationIdOrderByDatePaiementDescCreatedAtDesc(orgId, pageable).map(this::toResponse);
    }

    @Transactional
    public PaiementResponse enregistrer(PaiementRequest req, UUID orgId, UUID userId) {
        BigDecimal sumLignes =
                req.factures().stream()
                        .map(PaiementLigneRequest::montant)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumLignes.compareTo(req.montantTotal()) != 0) {
            throw BusinessException.badRequest("PAIEMENT_TOTAL_INCOHERENT");
        }

        Paiement p = new Paiement();
        p.setOrganisationId(orgId);
        p.setDatePaiement(req.datePaiement());
        p.setMontantTotal(req.montantTotal());
        p.setDevise(req.devise());
        p.setCompte(req.compte());
        p.setMoyenPaiement(req.moyenPaiement());
        p.setNotes(req.notes());
        p.setCreatedBy(utilisateurRepository.getReferenceById(userId));
        p = paiementRepository.save(p);

        for (PaiementLigneRequest ligne : req.factures()) {
            Facture f =
                    factureRepository
                            .findById(ligne.factureId())
                            .orElseThrow(() -> BusinessException.notFound("FACTURE_ABSENTE"));
            if (!f.getOrganisationId().equals(orgId)) {
                throw BusinessException.forbidden("FACTURE_ORG_MISMATCH");
            }
            if (f.getStatut() != StatutFacture.A_PAYER) {
                throw BusinessException.badRequest("FACTURE_NON_PAYABLE");
            }
            BigDecimal deja = facturePaiementRepository.sumMontantByFactureId(f.getId());
            BigDecimal apres = deja.add(ligne.montant());
            if (apres.compareTo(f.getMontantTtc()) > 0) {
                throw BusinessException.badRequest("PAIEMENT_MONTANT_DEPASSE");
            }
            FacturePaiement fp = new FacturePaiement();
            fp.setFactureId(f.getId());
            fp.setPaiementId(p.getId());
            fp.setFacture(f);
            fp.setPaiement(p);
            fp.setMontant(ligne.montant());
            facturePaiementRepository.save(fp);
            factureService.mettreAJourStatutApresPaiement(f.getId());
        }

        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("montantTotal", p.getMontantTotal());
        snap.put("datePaiement", p.getDatePaiement());
        auditLogService.log(orgId, userId, "CREATE", "Paiement", p.getId(), null, snap);
        return toResponse(p);
    }

    private PaiementResponse toResponse(Paiement p) {
        LocalDateTime created =
                p.getCreatedAt() == null
                        ? null
                        : LocalDateTime.ofInstant(p.getCreatedAt(), ZoneId.systemDefault());
        return new PaiementResponse(
                p.getId(),
                p.getDatePaiement(),
                p.getMontantTotal(),
                p.getDevise(),
                p.getCompte(),
                p.getMoyenPaiement(),
                p.getNotes(),
                created);
    }
}
