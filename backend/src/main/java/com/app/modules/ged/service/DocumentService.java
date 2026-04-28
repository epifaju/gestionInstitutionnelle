package com.app.modules.ged.service;

import com.app.config.GedProperties;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.ged.dto.DocumentResponse;
import com.app.modules.ged.dto.DocumentUpdateRequest;
import com.app.modules.ged.dto.DocumentUploadRequest;
import com.app.modules.ged.entity.Document;
import com.app.modules.ged.entity.DocumentAcces;
import com.app.modules.ged.entity.DocumentAccesId;
import com.app.modules.ged.entity.VisibiliteDoc;
import com.app.modules.ged.repository.DocumentAccesRepository;
import com.app.modules.ged.repository.DocumentRepository;
import com.app.modules.rh.repository.SalarieRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Pattern SAFE = Pattern.compile("[^a-zA-Z0-9._-]");
    private static final int EXPIRING_DEFAULT_DAYS = 30;

    private final DocumentRepository documentRepository;
    private final DocumentAccesRepository documentAccesRepository;
    private final MinioStorageService minioStorageService;
    private final UtilisateurRepository utilisateurRepository;
    private final SalarieRepository salarieRepository;
    private final GedProperties gedProperties;

    private final Tika tika = new Tika();

    @Transactional(readOnly = true)
    public Page<DocumentResponse> search(
            UUID orgId,
            String query,
            String type,
            String[] tags,
            String service,
            Boolean expirantBientot,
            Pageable p) {
        String ts = toTsQuery(query);
        String t = normalizeOptional(type);
        String s = normalizeOptional(service);
        boolean expSoon = Boolean.TRUE.equals(expirantBientot);

        String[] normalizedTags = normalizeTags(tags);
        boolean tagsEmpty = normalizedTags == null || normalizedTags.length == 0;

        return documentRepository
                .search(orgId, ts, t, normalizedTags, tagsEmpty, s, expSoon, EXPIRING_DEFAULT_DAYS, p)
                .map(d -> toResponse(d, null, null));
    }

    @Transactional
    public DocumentResponse upload(DocumentUploadRequest req, MultipartFile file, UUID orgId, UUID userId) throws Exception {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("FICHIER_ABSENT");
        }
        if (file.getSize() > gedProperties.getMaxFileBytes()) {
            throw BusinessException.badRequest("FICHIER_TROP_GRAND");
        }

        byte[] bytes = file.getBytes();
        String mime = tika.detect(bytes, file.getOriginalFilename());
        if (mime == null) mime = "application/octet-stream";
        if (!gedProperties.getAllowedMimeTypes().contains(mime)) {
            throw BusinessException.badRequest("FICHIER_TYPE_INVALIDE");
        }

        Document parent = null;
        int version = 1;
        if (req.documentParentId() != null) {
            parent = loadOwned(req.documentParentId(), orgId);
            version = parent.getVersion() + 1;
        }

        String typeDoc = normalizeRequired(req.typeDocument(), "TYPE_DOCUMENT_INVALIDE");
        int year = LocalDate.now().getYear();
        String rawName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String safe = SAFE.matcher(rawName).replaceAll("_");
        String objectName = orgId + "/ged/" + typeDoc + "/" + year + "/" + UUID.randomUUID() + "-" + safe;

        minioStorageService.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, mime);

        Document d = new Document();
        d.setOrganisationId(orgId);
        d.setTitre(req.titre().trim());
        d.setDescription(normalizeOptional(req.description()));
        d.setTypeDocument(typeDoc);
        d.setTags(normalizeTags(req.tags()));
        d.setFichierUrl(objectName);
        d.setNomFichier(rawName);
        d.setTailleOctets(file.getSize());
        d.setMimeType(mime);
        d.setVersion(version);
        d.setDocumentParent(parent);
        d.setVisibilite(parseVisibilite(req.visibilite()));
        d.setServiceCible(normalizeOptional(req.serviceCible()));
        d.setEntiteLieeType(normalizeOptional(req.entiteLieeType()));
        d.setEntiteLieeId(req.entiteLieeId());
        d.setDateExpiration(req.dateExpiration());
        d.setUploadePar(userId);
        d = documentRepository.save(d);

        String uploaderName = resolveUploaderName(userId);
        String presigned = minioStorageService.presignedGetUrl(objectName, gedProperties.getPresignExpirySeconds());
        return toResponse(d, uploaderName, presigned);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getById(UUID id, UUID orgId, UUID userId) {
        Document d = loadOwned(id, orgId);
        assertCanAccess(d, orgId, userId);
        String uploaderName = resolveUploaderName(d.getUploadePar());
        return toResponse(d, uploaderName, null);
    }

    @Transactional(readOnly = true)
    public String generatePresignedUrl(UUID id, UUID orgId, UUID userId) throws Exception {
        Document d = loadOwned(id, orgId);
        assertCanAccess(d, orgId, userId);
        return minioStorageService.presignedGetUrl(d.getFichierUrl(), gedProperties.getPresignExpirySeconds());
    }

    @Transactional(readOnly = true)
    public String getObjectName(UUID id, UUID orgId, UUID userId) {
        Document d = loadOwned(id, orgId);
        assertCanAccess(d, orgId, userId);
        return d.getFichierUrl();
    }

    @Transactional
    public DocumentResponse modifier(UUID id, DocumentUpdateRequest req, UUID orgId, UUID userId) {
        Document d = loadOwned(id, orgId);
        assertCanModify(d, orgId, userId);

        d.setTitre(req.titre().trim());
        d.setDescription(normalizeOptional(req.description()));
        d.setTags(normalizeTags(req.tags()));
        d.setVisibilite(parseVisibilite(req.visibilite()));
        d.setServiceCible(normalizeOptional(req.serviceCible()));
        d.setEntiteLieeType(normalizeOptional(req.entiteLieeType()));
        d.setEntiteLieeId(req.entiteLieeId());
        d.setDateExpiration(req.dateExpiration());
        d = documentRepository.save(d);
        return toResponse(d, resolveUploaderName(d.getUploadePar()), null);
    }

    @Transactional
    public void supprimer(UUID id, UUID orgId, UUID userId) {
        Document d = loadOwned(id, orgId);
        assertCanDelete(d, orgId, userId);
        if (!d.isSupprime()) {
            d.setSupprime(true);
            d.setDateSuppression(Instant.now());
            documentRepository.save(d);
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<DocumentResponse> getVersions(UUID documentParentId, UUID orgId, UUID userId) {
        Document root = loadOwned(documentParentId, orgId);
        assertCanAccess(root, orgId, userId);

        var uploaderName = resolveUploaderName(root.getUploadePar());
        var out = new java.util.ArrayList<DocumentResponse>();
        out.add(toResponse(root, uploaderName, null));

        var children = documentRepository.findAllByOrganisationIdAndSupprimeFalseAndDocumentParent_IdOrderByVersionDesc(orgId, documentParentId);
        for (Document d : children) {
            out.add(toResponse(d, resolveUploaderName(d.getUploadePar()), null));
        }
        out.sort((a, b) -> Integer.compare(b.version(), a.version()));
        return out;
    }

    @Transactional(readOnly = true)
    public java.util.List<DocumentResponse> getDocumentsExpirantBientot(UUID orgId, int nbJours) {
        int days = nbJours <= 0 ? EXPIRING_DEFAULT_DAYS : nbJours;
        LocalDate limit = LocalDate.now().plusDays(days);
        return documentRepository.findExpiringBefore(orgId, limit).stream()
                .map(d -> toResponse(d, resolveUploaderName(d.getUploadePar()), null))
                .toList();
    }

    @Transactional
    public void partagerAvec(UUID docId, UUID utilisateurId, boolean peutModifier, boolean peutSupprimer, UUID orgId) {
        Document d = loadOwned(docId, orgId);
        DocumentAccesId id = new DocumentAccesId();
        id.setDocumentId(d.getId());
        id.setUtilisateurId(utilisateurId);

        DocumentAcces acc = documentAccesRepository.findById(id).orElseGet(() -> {
            DocumentAcces x = new DocumentAcces();
            x.setId(id);
            x.setCreatedAt(Instant.now());
            return x;
        });
        acc.setPeutModifier(peutModifier);
        acc.setPeutSupprimer(peutSupprimer);
        documentAccesRepository.save(acc);
    }

    // --- Access rules ---

    void assertCanAccess(Document d, UUID orgId, UUID userId) {
        if (!orgId.equals(d.getOrganisationId()) || d.isSupprime()) {
            throw BusinessException.notFound("DOCUMENT_ABSENT");
        }
        if (canAccess(d, orgId, userId)) {
            return;
        }
        throw BusinessException.forbidden("DOCUMENT_ACCES_REFUSE");
    }

    void assertCanModify(Document d, UUID orgId, UUID userId) {
        if (userId != null && userId.equals(d.getUploadePar())) {
            return;
        }
        documentAccesRepository.findById_DocumentIdAndId_UtilisateurId(d.getId(), userId)
                .filter(DocumentAcces::isPeutModifier)
                .ifPresentOrElse(__ -> {}, () -> { throw BusinessException.forbidden("DOCUMENT_MODIF_REFUSE"); });
    }

    void assertCanDelete(Document d, UUID orgId, UUID userId) {
        if (userId != null && userId.equals(d.getUploadePar())) {
            return;
        }
        documentAccesRepository.findById_DocumentIdAndId_UtilisateurId(d.getId(), userId)
                .filter(DocumentAcces::isPeutSupprimer)
                .ifPresentOrElse(__ -> {}, () -> { throw BusinessException.forbidden("DOCUMENT_SUPPR_REFUSE"); });
    }

    private boolean canAccess(Document d, UUID orgId, UUID userId) {
        if (userId != null && userId.equals(d.getUploadePar())) {
            return true;
        }
        if (d.getVisibilite() == VisibiliteDoc.ORGANISATION || d.getVisibilite() == VisibiliteDoc.PUBLIC) {
            return true;
        }
        if (d.getVisibilite() == VisibiliteDoc.SERVICE) {
            String cible = normalizeOptional(d.getServiceCible());
            if (cible == null) {
                return true;
            }
            String userService = salarieRepository
                    .findByOrganisationIdAndUtilisateur_Id(orgId, userId)
                    .map(s -> normalizeOptional(s.getService()))
                    .orElse(null);
            return cible.equals(userService);
        }
        // PRIVE
        return documentAccesRepository.findById_DocumentIdAndId_UtilisateurId(d.getId(), userId).isPresent();
    }

    private Document loadOwned(UUID id, UUID orgId) {
        Document d = documentRepository.findById(id).orElseThrow(() -> BusinessException.notFound("DOCUMENT_ABSENT"));
        if (!orgId.equals(d.getOrganisationId()) || d.isSupprime()) {
            throw BusinessException.notFound("DOCUMENT_ABSENT");
        }
        return d;
    }

    private String resolveUploaderName(UUID uploaderId) {
        if (uploaderId == null) return null;
        return utilisateurRepository.findById(uploaderId)
                .map(u -> ((u.getNom() == null ? "" : u.getNom()) + " " + (u.getPrenom() == null ? "" : u.getPrenom())).trim())
                .filter(s -> !s.isBlank())
                .orElse(null);
    }

    private static String normalizeOptional(String s) {
        if (s == null) return null;
        String x = s.trim();
        return x.isBlank() ? null : x;
    }

    private static String normalizeRequired(String s, String code) {
        if (s == null || s.trim().isBlank()) throw BusinessException.badRequest(code);
        return s.trim();
    }

    private static String[] normalizeTags(String[] tags) {
        if (tags == null || tags.length == 0) return null;
        return Arrays.stream(tags)
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim())
                .distinct()
                .toArray(String[]::new);
    }

    private static VisibiliteDoc parseVisibilite(String raw) {
        if (raw == null || raw.isBlank()) return VisibiliteDoc.ORGANISATION;
        try {
            return VisibiliteDoc.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return VisibiliteDoc.ORGANISATION;
        }
    }

    private static String toTsQuery(String query) {
        if (query == null || query.isBlank()) return null;
        String[] parts = query.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            String t = p.replaceAll("[^\\p{L}\\p{Nd}_-]", "");
            if (t.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(" & ");
            sb.append(t).append(":*");
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static DocumentResponse toResponse(Document d, String uploaderName, String presignedUrl) {
        LocalDateTime created =
                d.getCreatedAt() == null ? null : LocalDateTime.ofInstant(d.getCreatedAt(), ZoneId.systemDefault());
        return new DocumentResponse(
                d.getId(),
                d.getTitre(),
                d.getDescription(),
                d.getTypeDocument(),
                d.getTags(),
                d.getNomFichier(),
                d.getTailleOctets(),
                d.getMimeType(),
                d.getVersion(),
                d.getDocumentParent() != null ? d.getDocumentParent().getId() : null,
                d.getVisibilite() != null ? d.getVisibilite().name() : null,
                d.getServiceCible(),
                d.getEntiteLieeType(),
                d.getEntiteLieeId(),
                d.getDateExpiration(),
                uploaderName,
                created,
                presignedUrl
        );
    }
}

