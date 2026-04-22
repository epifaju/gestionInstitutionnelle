package com.app.modules.finance.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.finance.dto.FactureRequest;
import com.app.modules.finance.dto.FactureResponse;
import com.app.modules.finance.entity.CategorieDepense;
import com.app.modules.finance.entity.Facture;
import com.app.modules.finance.entity.StatutFacture;
import com.app.modules.finance.repository.CategorieDepenseRepository;
import com.app.modules.finance.repository.FacturePaiementRepository;
import com.app.modules.finance.repository.FactureRepository;
import com.app.modules.finance.repository.FactureSpecifications;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FactureService {

    private static final long MAX_FILE = 10_485_760L;
    private static final Pattern SAFE = Pattern.compile("[^a-zA-Z0-9._-]");

    private final FactureRepository factureRepository;
    private final FacturePaiementRepository facturePaiementRepository;
    private final CategorieDepenseRepository categorieDepenseRepository;
    private final OrganisationRepository organisationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final FactureSequenceService factureSequenceService;
    private final TauxChangeService tauxChangeService;
    private final MinioStorageService minioStorageService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<FactureResponse> list(
            UUID orgId,
            String statut,
            UUID categorieId,
            LocalDate debut,
            LocalDate fin,
            String fournisseur,
            BigDecimal montantMin,
            BigDecimal montantMax,
            Pageable pageable) {
        StatutFacture st = parseStatut(statut);
        String four = fournisseur == null || fournisseur.isBlank() ? null : fournisseur.trim();

        Specification<Facture> spec =
                Specification.where(FactureSpecifications.organisationId(orgId))
                        .and(FactureSpecifications.statutOptional(st))
                        .and(FactureSpecifications.categorieIdOptional(categorieId))
                        .and(FactureSpecifications.dateDebutOptional(debut))
                        .and(FactureSpecifications.dateFinOptional(fin))
                        .and(FactureSpecifications.fournisseurOptional(four))
                        .and(FactureSpecifications.montantMinOptional(montantMin))
                        .and(FactureSpecifications.montantMaxOptional(montantMax));

        Pageable sorted =
                pageable.getSort().isSorted()
                        ? pageable
                        : PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                Sort.by(Sort.Order.desc("dateFacture"), Sort.Order.desc("createdAt")));

        return factureRepository.findAll(spec, sorted).map(f -> toResponse(f, true));
    }

    @Transactional(readOnly = true)
    public FactureResponse getById(UUID id, UUID orgId) {
        Facture f = loadOwned(id, orgId);
        return toResponse(f, true);
    }

    @Transactional(readOnly = true)
    public String getJustificatifObjectName(UUID id, UUID orgId) {
        Facture f = loadOwned(id, orgId);
        String just = f.getJustificatifUrl();
        if (just == null || just.isBlank()) {
            throw BusinessException.notFound("JUSTIFICATIF_ABSENT");
        }
        // Hard guard: object name must include orgId and facture id
        String expectedPrefix = "factures/" + orgId + "/" + id + "/";
        if (!just.startsWith(expectedPrefix)) {
            throw BusinessException.forbidden("JUSTIFICATIF_ORG_MISMATCH");
        }
        return just;
    }

    @Transactional
    public FactureResponse creer(FactureRequest req, MultipartFile justificatif, UUID orgId, UUID userId) throws Exception {
        StatutFacture st = StatutFacture.valueOf(req.statut().trim().toUpperCase());
        int annee = req.dateFacture().getYear();
        int seq = factureSequenceService.nextSequence(orgId, annee);
        String reference = "FAC-" + annee + "-" + String.format("%04d", seq);

        BigDecimal ttc = calculerTtc(req.montantHt(), req.tva());
        BigDecimal taux = tauxChangeService.tauxVersEur(orgId, req.devise(), req.dateFacture());

        Facture f = new Facture();
        f.setOrganisationId(orgId);
        f.setReference(reference);
        f.setFournisseur(req.fournisseur());
        f.setDateFacture(req.dateFacture());
        f.setMontantHt(req.montantHt());
        f.setTva(req.tva());
        f.setMontantTtc(ttc);
        f.setDevise(req.devise());
        f.setTauxChangeEur(taux);
        f.setStatut(st);
        f.setNotes(req.notes());
        f.setCreatedBy(utilisateurRepository.getReferenceById(userId));
        if (req.categorieId() != null) {
            CategorieDepense cat =
                    categorieDepenseRepository
                            .findById(req.categorieId())
                            .filter(c -> c.getOrganisationId().equals(orgId))
                            .orElseThrow(() -> BusinessException.notFound("CATEGORIE_ABSENTE"));
            f.setCategorie(cat);
        }
        f = factureRepository.save(f);

        if (justificatif != null && !justificatif.isEmpty()) {
            String url = uploadAndStore(f.getId(), orgId, justificatif);
            f.setJustificatifUrl(url);
            f = factureRepository.save(f);
        }

        validerJustificatifObligatoire(orgId, f);
        auditLogService.log(orgId, userId, "CREATE", "Facture", f.getId(), null, snapshot(f));
        return toResponse(f, true);
    }

    @Transactional
    public FactureResponse modifier(UUID id, FactureRequest req, UUID orgId, UUID userId) {
        Facture f = loadOwned(id, orgId);
        if (f.getStatut() == StatutFacture.PAYE) {
            throw BusinessException.badRequest("FACTURE_DEJA_PAYEE");
        }
        Map<String, Object> avant = snapshot(f);
        f.setFournisseur(req.fournisseur());
        f.setDateFacture(req.dateFacture());
        f.setMontantHt(req.montantHt());
        f.setTva(req.tva());
        f.setMontantTtc(calculerTtc(req.montantHt(), req.tva()));
        f.setDevise(req.devise());
        f.setTauxChangeEur(tauxChangeService.tauxVersEur(orgId, req.devise(), req.dateFacture()));
        f.setStatut(StatutFacture.valueOf(req.statut().trim().toUpperCase()));
        f.setNotes(req.notes());
        if (req.categorieId() != null) {
            CategorieDepense cat =
                    categorieDepenseRepository
                            .findById(req.categorieId())
                            .filter(c -> c.getOrganisationId().equals(orgId))
                            .orElseThrow(() -> BusinessException.notFound("CATEGORIE_ABSENTE"));
            f.setCategorie(cat);
        } else {
            f.setCategorie(null);
        }
        f = factureRepository.save(f);
        validerJustificatifObligatoire(orgId, f);
        auditLogService.log(orgId, userId, "UPDATE", "Facture", f.getId(), avant, snapshot(f));
        return toResponse(f, true);
    }

    @Transactional
    public FactureResponse changerStatut(UUID id, String nouveauStatut, UUID orgId, UUID userId) {
        Facture f = loadOwned(id, orgId);
        StatutFacture next = StatutFacture.valueOf(nouveauStatut.trim().toUpperCase());
        if (next == StatutFacture.PAYE) {
            throw BusinessException.badRequest("FACTURE_STATUT_TRANSITION");
        }
        StatutFacture cur = f.getStatut();
        boolean ok =
                (cur == StatutFacture.BROUILLON && (next == StatutFacture.A_PAYER || next == StatutFacture.ANNULE))
                        || (cur == StatutFacture.A_PAYER && (next == StatutFacture.ANNULE));
        if (!ok) {
            throw BusinessException.badRequest("FACTURE_STATUT_TRANSITION");
        }
        if (cur == StatutFacture.BROUILLON && next == StatutFacture.A_PAYER) {
            validerJustificatifObligatoire(orgId, f);
        }
        Map<String, Object> avant = snapshot(f);
        f.setStatut(next);
        f = factureRepository.save(f);
        auditLogService.log(orgId, userId, "UPDATE", "Facture", f.getId(), avant, snapshot(f));
        return toResponse(f, true);
    }

    @Transactional
    public void uploadJustificatif(UUID id, MultipartFile file, UUID orgId, UUID userId) throws Exception {
        Facture f = loadOwned(id, orgId);
        Map<String, Object> avant = snapshot(f);
        String url = uploadAndStore(f.getId(), orgId, file);
        f.setJustificatifUrl(url);
        factureRepository.save(f);
        auditLogService.log(orgId, userId, "UPDATE", "Facture", f.getId(), avant, snapshot(f));
    }

    @Transactional(readOnly = true)
    public MinioStorageService.Download downloadJustificatif(String objectName) throws Exception {
        return minioStorageService.download(objectName);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculerMontantPaye(UUID factureId) {
        return facturePaiementRepository.sumMontantByFactureId(factureId);
    }

    @Transactional
    public void mettreAJourStatutApresPaiement(UUID factureId) {
        Facture f = factureRepository.findById(factureId).orElseThrow(() -> BusinessException.notFound("FACTURE_ABSENTE"));
        BigDecimal paye = facturePaiementRepository.sumMontantByFactureId(factureId);
        if (paye.compareTo(f.getMontantTtc()) >= 0) {
            f.setStatut(StatutFacture.PAYE);
            factureRepository.save(f);
        }
    }

    private void validerJustificatifObligatoire(UUID orgId, Facture f) {
        Organisation org = organisationRepository.findById(orgId).orElseThrow(() -> BusinessException.notFound("ORG_ABSENTE"));
        if (f.getStatut() != StatutFacture.BROUILLON
                && f.getMontantTtc().compareTo(org.getSeuilJustificatif()) > 0
                && (f.getJustificatifUrl() == null || f.getJustificatifUrl().isBlank())) {
            throw BusinessException.badRequest("JUSTIFICATIF_REQUIS");
        }
    }

    private String uploadAndStore(UUID factureId, UUID orgId, MultipartFile file) throws Exception {
        if (file.getSize() > MAX_FILE) {
            throw BusinessException.badRequest("FICHIER_TROP_GRAND");
        }
        byte[] bytes = file.getBytes();
        String mime = new Tika().detect(bytes, file.getOriginalFilename());
        boolean ok = "application/pdf".equals(mime) || (mime != null && mime.startsWith("image/"));
        if (!ok) {
            throw BusinessException.badRequest("FICHIER_TYPE_INVALIDE");
        }
        String raw = file.getOriginalFilename() != null ? file.getOriginalFilename() : "justificatif";
        String safe = SAFE.matcher(raw).replaceAll("_");
        String objectName = "factures/" + orgId + "/" + factureId + "/" + safe;
        minioStorageService.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, mime);
        return objectName;
    }

    private Facture loadOwned(UUID id, UUID orgId) {
        Facture f = factureRepository.findById(id).orElseThrow(() -> BusinessException.notFound("FACTURE_ABSENTE"));
        if (!f.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("FACTURE_ORG_MISMATCH");
        }
        return f;
    }

    private static BigDecimal calculerTtc(BigDecimal ht, BigDecimal tvaPct) {
        BigDecimal facteur = BigDecimal.ONE.add(tvaPct.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
        return ht.multiply(facteur).setScale(2, RoundingMode.HALF_UP);
    }

    private static StatutFacture parseStatut(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return StatutFacture.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    FactureResponse toResponse(Facture f, boolean presignJustificatif) {
        BigDecimal paye = facturePaiementRepository.sumMontantByFactureId(f.getId());
        BigDecimal restant = f.getMontantTtc().subtract(paye).max(BigDecimal.ZERO);
        BigDecimal ttcEur = f.getMontantTtc().multiply(f.getTauxChangeEur()).setScale(2, RoundingMode.HALF_UP);
        UUID catId = f.getCategorie() != null ? f.getCategorie().getId() : null;
        String catLib = f.getCategorie() != null ? f.getCategorie().getLibelle() : null;
        String just = f.getJustificatifUrl();
        if (presignJustificatif && just != null && !just.isBlank()) {
            just = "/api/v1/finance/factures/" + f.getId() + "/justificatif";
        }
        LocalDateTime created =
                f.getCreatedAt() == null
                        ? null
                        : LocalDateTime.ofInstant(f.getCreatedAt(), ZoneId.systemDefault());
        return new FactureResponse(
                f.getId(),
                f.getReference(),
                f.getFournisseur(),
                f.getDateFacture(),
                f.getMontantHt(),
                f.getTva(),
                f.getMontantTtc(),
                f.getDevise(),
                f.getTauxChangeEur(),
                ttcEur,
                catId,
                catLib,
                f.getStatut().name(),
                just,
                paye,
                restant,
                created);
    }

    private Map<String, Object> snapshot(Facture f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reference", f.getReference());
        m.put("fournisseur", f.getFournisseur());
        m.put("statut", f.getStatut() != null ? f.getStatut().name() : null);
        m.put("montantTtc", f.getMontantTtc());
        return m;
    }
}
