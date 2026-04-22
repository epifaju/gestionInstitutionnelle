package com.app.modules.finance.service;

import com.app.audit.AuditLogService;
import com.app.modules.finance.dto.RecetteRequest;
import com.app.modules.finance.dto.RecetteResponse;
import com.app.modules.finance.entity.CategorieDepense;
import com.app.modules.finance.entity.Recette;
import com.app.modules.finance.entity.TypeRecette;
import com.app.modules.finance.repository.CategorieDepenseRepository;
import com.app.modules.finance.repository.RecetteRepository;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RecetteService {

    private static final long MAX_FILE = 10_485_760L;
    private static final Pattern SAFE = Pattern.compile("[^a-zA-Z0-9._-]");

    private final RecetteRepository recetteRepository;
    private final CategorieDepenseRepository categorieDepenseRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final TauxChangeService tauxChangeService;
    private final MinioStorageService minioStorageService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<RecetteResponse> list(UUID orgId, String type, java.time.LocalDate debut, java.time.LocalDate fin, Pageable p) {
        TypeRecette tr = parseType(type);
        return recetteRepository.search(orgId, tr, debut, fin, p).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public String getJustificatifObjectName(UUID id, UUID orgId) {
        Recette r = recetteRepository.findById(id).orElseThrow(() -> BusinessException.notFound("RECETTE_ABSENTE"));
        if (!r.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("RECETTE_ORG_MISMATCH");
        }
        String just = r.getJustificatifUrl();
        if (just == null || just.isBlank()) {
            throw BusinessException.notFound("JUSTIFICATIF_ABSENT");
        }
        String expectedPrefix = "recettes/" + orgId + "/" + id + "/";
        if (!just.startsWith(expectedPrefix)) {
            throw BusinessException.forbidden("JUSTIFICATIF_ORG_MISMATCH");
        }
        return just;
    }

    @Transactional
    public RecetteResponse creer(RecetteRequest req, MultipartFile justificatif, UUID orgId, UUID userId) throws Exception {
        TypeRecette tr = TypeRecette.valueOf(req.typeRecette().trim().toUpperCase());
        BigDecimal taux = tauxChangeService.tauxVersEur(orgId, req.devise(), req.dateRecette());

        Recette r = new Recette();
        r.setOrganisationId(orgId);
        r.setDateRecette(req.dateRecette());
        r.setMontant(req.montant());
        r.setDevise(req.devise());
        r.setTauxChangeEur(taux);
        r.setTypeRecette(tr);
        r.setDescription(req.description());
        r.setModeEncaissement(req.modeEncaissement());
        r.setCreatedBy(utilisateurRepository.getReferenceById(userId));
        if (req.categorieId() != null) {
            CategorieDepense cat =
                    categorieDepenseRepository
                            .findById(req.categorieId())
                            .filter(c -> c.getOrganisationId().equals(orgId))
                            .orElseThrow(() -> BusinessException.notFound("CATEGORIE_ABSENTE"));
            r.setCategorie(cat);
        }
        r = recetteRepository.save(r);

        if (justificatif != null && !justificatif.isEmpty()) {
            if (justificatif.getSize() > MAX_FILE) {
                throw BusinessException.badRequest("FICHIER_TROP_GRAND");
            }
            byte[] bytes = justificatif.getBytes();
            String mime = new Tika().detect(bytes, justificatif.getOriginalFilename());
            boolean ok = "application/pdf".equals(mime) || (mime != null && mime.startsWith("image/"));
            if (!ok) {
                throw BusinessException.badRequest("FICHIER_TYPE_INVALIDE");
            }
            String raw = justificatif.getOriginalFilename() != null ? justificatif.getOriginalFilename() : "justificatif";
            String safe = SAFE.matcher(raw).replaceAll("_");
            String objectName = "recettes/" + orgId + "/" + r.getId() + "/" + safe;
            minioStorageService.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, mime);
            r.setJustificatifUrl(objectName);
            r = recetteRepository.save(r);
        }

        auditLogService.log(orgId, userId, "CREATE", "Recette", r.getId(), null, snapshot(r));
        return toResponse(r);
    }

    @Transactional
    public RecetteResponse modifier(UUID id, RecetteRequest req, UUID orgId, UUID userId) {
        Recette r =
                recetteRepository
                        .findById(id)
                        .orElseThrow(() -> BusinessException.notFound("RECETTE_ABSENTE"));
        if (!r.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("RECETTE_ORG_MISMATCH");
        }

        Map<String, Object> avant = snapshot(r);

        TypeRecette tr = TypeRecette.valueOf(req.typeRecette().trim().toUpperCase());
        BigDecimal taux = tauxChangeService.tauxVersEur(orgId, req.devise(), req.dateRecette());

        r.setDateRecette(req.dateRecette());
        r.setMontant(req.montant());
        r.setDevise(req.devise());
        r.setTauxChangeEur(taux);
        r.setTypeRecette(tr);
        r.setDescription(req.description());
        r.setModeEncaissement(req.modeEncaissement());

        if (req.categorieId() != null) {
            CategorieDepense cat =
                    categorieDepenseRepository
                            .findById(req.categorieId())
                            .filter(c -> c.getOrganisationId().equals(orgId))
                            .orElseThrow(() -> BusinessException.notFound("CATEGORIE_ABSENTE"));
            r.setCategorie(cat);
        } else {
            r.setCategorie(null);
        }

        r = recetteRepository.save(r);
        auditLogService.log(orgId, userId, "UPDATE", "Recette", r.getId(), avant, snapshot(r));
        return toResponse(r);
    }

    @Transactional(readOnly = true)
    public MinioStorageService.Download downloadJustificatif(String objectName) throws Exception {
        return minioStorageService.download(objectName);
    }

    @Transactional
    public void uploadJustificatif(UUID id, MultipartFile file, UUID orgId, UUID userId) throws Exception {
        Recette r =
                recetteRepository
                        .findById(id)
                        .orElseThrow(() -> BusinessException.notFound("RECETTE_ABSENTE"));
        if (!r.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("RECETTE_ORG_MISMATCH");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("FICHIER_ABSENT");
        }

        Map<String, Object> avant = snapshot(r);
        String old = r.getJustificatifUrl();
        String objectName = uploadAndStore(r.getId(), orgId, file);
        r.setJustificatifUrl(objectName);
        recetteRepository.save(r);
        auditLogService.log(orgId, userId, "UPDATE", "Recette", r.getId(), avant, snapshot(r));

        // Best-effort : supprimer l'ancien justificatif si différent
        if (old != null && !old.isBlank() && !old.equals(objectName)) {
            try {
                minioStorageService.delete(old);
            } catch (Exception ignored) {
            }
        }
    }

    @Transactional
    public void supprimer(UUID id, UUID orgId, UUID userId) {
        Recette r =
                recetteRepository
                        .findById(id)
                        .orElseThrow(() -> BusinessException.notFound("RECETTE_ABSENTE"));
        if (!r.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("RECETTE_ORG_MISMATCH");
        }

        Map<String, Object> avant = snapshot(r);
        String objectName = r.getJustificatifUrl();
        recetteRepository.delete(r);
        auditLogService.log(orgId, userId, "DELETE", "Recette", id, avant, null);

        // Best-effort : supprimer le justificatif en stockage (si présent)
        if (objectName != null && !objectName.isBlank()) {
            try {
                minioStorageService.delete(objectName);
            } catch (Exception ignored) {
                // ne pas bloquer la suppression métier si la suppression MinIO échoue
            }
        }
    }

    private RecetteResponse toResponse(Recette r) {
        BigDecimal montantEur = r.getMontant().multiply(r.getTauxChangeEur()).setScale(2, RoundingMode.HALF_UP);
        String cat = r.getCategorie() != null ? r.getCategorie().getLibelle() : null;
        UUID catId = r.getCategorie() != null ? r.getCategorie().getId() : null;
        String just = r.getJustificatifUrl();
        if (just != null && !just.isBlank()) {
            just = "/api/v1/finance/recettes/" + r.getId() + "/justificatif";
        }
        LocalDateTime created =
                r.getCreatedAt() == null
                        ? null
                        : LocalDateTime.ofInstant(r.getCreatedAt(), ZoneId.systemDefault());
        return new RecetteResponse(
                r.getId(),
                r.getDateRecette(),
                r.getMontant(),
                r.getDevise(),
                r.getTauxChangeEur(),
                montantEur,
                r.getTypeRecette().name(),
                r.getDescription(),
                r.getModeEncaissement(),
                just,
                catId,
                cat,
                created);
    }

    private Map<String, Object> snapshot(Recette r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("montant", r.getMontant());
        m.put("typeRecette", r.getTypeRecette() != null ? r.getTypeRecette().name() : null);
        m.put("dateRecette", r.getDateRecette());
        m.put("devise", r.getDevise());
        return m;
    }

    private String uploadAndStore(UUID recetteId, UUID orgId, MultipartFile file) throws Exception {
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
        String objectName = "recettes/" + orgId + "/" + recetteId + "/" + safe;
        minioStorageService.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, mime);
        return objectName;
    }

    private static TypeRecette parseType(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return TypeRecette.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
