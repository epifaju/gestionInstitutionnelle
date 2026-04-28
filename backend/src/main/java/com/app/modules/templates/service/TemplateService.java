package com.app.modules.templates.service;

import com.app.audit.AuditLogService;
import com.app.modules.ged.dto.DocumentUploadRequest;
import com.app.modules.ged.service.DocumentService;
import com.app.modules.templates.dto.CreateTemplateRequest;
import com.app.modules.templates.dto.GeneratedDocumentResponse;
import com.app.modules.templates.dto.TemplateDefinitionResponse;
import com.app.modules.templates.dto.TemplateRevisionResponse;
import com.app.modules.templates.dto.UpdateTemplateRequest;
import com.app.modules.templates.entity.GeneratedDocument;
import com.app.modules.templates.entity.TemplateDefinition;
import com.app.modules.templates.entity.TemplateFormat;
import com.app.modules.templates.entity.TemplateRevision;
import com.app.modules.templates.repository.GeneratedDocumentRepository;
import com.app.modules.templates.repository.TemplateDefinitionRepository;
import com.app.modules.templates.repository.TemplateRevisionRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateDefinitionRepository definitionRepository;
    private final TemplateRevisionRepository revisionRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final TemplateRenderService renderService;
    private final TemplateDataService dataService;
    private final DocumentService documentService;
    private final AuditLogService auditLogService;
    private final MinioStorageService minioStorageService;

    private final Tika tika = new Tika();

    @Transactional(readOnly = true)
    public List<TemplateDefinitionResponse> list(UUID orgId) {
        return definitionRepository.findAll().stream()
                .filter(d -> orgId.equals(d.getOrganisationId()))
                .map(this::toResp)
                .toList();
    }

    @Transactional
    public TemplateDefinitionResponse create(CreateTemplateRequest req, UUID orgId, UUID userId) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (definitionRepository.findByOrganisationIdAndCode(orgId, code).isPresent()) {
            throw BusinessException.badRequest("TEMPLATE_CODE_DEJA_EXISTANT");
        }
        TemplateDefinition d = new TemplateDefinition();
        d.setOrganisationId(orgId);
        d.setCode(code);
        d.setLabel(req.label().trim());
        d.setCategory(req.category());
        d.setFormat(req.format());
        d.setDefaultLocale(req.defaultLocale());
        d.setCreatedBy(userId);
        d.setUpdatedAt(Instant.now());
        d = definitionRepository.save(d);
        auditLogService.log(orgId, userId, "CREATE_TEMPLATE", "TemplateDefinition", d.getId(), null, Map.of("code", d.getCode()));
        return toResp(d);
    }

    @Transactional
    public TemplateDefinitionResponse update(UUID id, UpdateTemplateRequest req, UUID orgId, UUID userId) {
        TemplateDefinition d = loadOwned(id, orgId);
        d.setLabel(req.label().trim());
        d.setStatus(req.status());
        d.setDefaultLocale(req.defaultLocale());
        d.setUpdatedAt(Instant.now());
        d = definitionRepository.save(d);
        auditLogService.log(orgId, userId, "UPDATE_TEMPLATE", "TemplateDefinition", d.getId(), null, Map.of("status", d.getStatus().name()));
        return toResp(d);
    }

    @Transactional(readOnly = true)
    public List<TemplateRevisionResponse> listRevisions(UUID templateId, UUID orgId) {
        TemplateDefinition d = loadOwned(templateId, orgId);
        return revisionRepository.findByTemplate_IdOrderByVersionDesc(d.getId()).stream().map(this::toResp).toList();
    }

    @Transactional
    public TemplateRevisionResponse addRevision(UUID templateId, MultipartFile file, String comment, UUID orgId, UUID userId) throws Exception {
        TemplateDefinition d = loadOwned(templateId, orgId);
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("FICHIER_ABSENT");

        byte[] bytes = file.getBytes();
        String mime = tika.detect(bytes, file.getOriginalFilename());
        if (mime == null) mime = "application/octet-stream";

        int nextVersion = revisionRepository.findTopByTemplate_IdOrderByVersionDesc(d.getId()).map(r -> r.getVersion() + 1).orElse(1);

        // Store as GED Document
        String typeDoc = "TEMPLATE_" + d.getCategory().name();
        var uploaded = documentService.upload(
                new DocumentUploadRequest(
                        d.getLabel() + " (v" + nextVersion + ")",
                        "Revision " + nextVersion + " for template " + d.getCode(),
                        typeDoc,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new InMemoryMultipartFile("file", file.getOriginalFilename(), mime, bytes),
                orgId,
                userId
        );

        TemplateRevision r = new TemplateRevision();
        r.setTemplate(d);
        r.setVersion(nextVersion);
        r.setContentDocumentId(uploaded.id());
        r.setContentMime(mime);
        r.setChecksum(sha256(bytes));
        r.setComment(comment);
        r.setCreatedBy(userId);
        r = revisionRepository.save(r);

        auditLogService.log(orgId, userId, "ADD_TEMPLATE_REVISION", "TemplateRevision", r.getId(), null, Map.of("templateCode", d.getCode(), "version", nextVersion));
        return toResp(r);
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentResponse> listGenerated(UUID orgId, String subjectType, UUID subjectId) {
        return generatedDocumentRepository.findByOrganisationIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(orgId, subjectType, subjectId)
                .stream().map(this::toResp).toList();
    }

    @Transactional
    public GeneratedDocumentResponse generate(
            String templateCode,
            String subjectType,
            UUID subjectId,
            TemplateOutputFormat outputFormat,
            boolean saveToGed,
            Map<String, String> overrides,
            UUID orgId,
            UUID userId
    ) throws Exception {
        TemplateDefinition d = definitionRepository.findByOrganisationIdAndCode(orgId, templateCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> BusinessException.notFound("TEMPLATE_ABSENT"));

        TemplateRevision rev = revisionRepository.findTopByTemplate_IdOrderByVersionDesc(d.getId())
                .orElseThrow(() -> BusinessException.notFound("TEMPLATE_REVISION_ABSENTE"));

        Map<String, String> values = dataService.buildValues(subjectType, subjectId, orgId);
        if (overrides != null && !overrides.isEmpty()) values.putAll(overrides);

        byte[] outBytes;
        String mime;
        String ext;

        if (d.getFormat() == TemplateFormat.HTML) {
            if (outputFormat != TemplateOutputFormat.HTML) throw BusinessException.badRequest("TEMPLATE_FORMAT_INVALIDE");
            String html = fetchTemplateHtml(rev, orgId, userId);
            outBytes = renderService.renderHtml(html, values);
            mime = "text/html; charset=utf-8";
            ext = "html";
        } else {
            byte[] docx = fetchTemplateDocx(rev, orgId, userId);
            byte[] rendered = renderService.renderDocx(docx, values);
            if (outputFormat == TemplateOutputFormat.PDF) {
                outBytes = renderService.convertDocxToPdf(rendered);
                mime = "application/pdf";
                ext = "pdf";
            } else {
                outBytes = rendered;
                mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                ext = "docx";
            }
        }

        UUID outputDocId = null;
        if (saveToGed) {
            String typeDoc = "GENERATED_" + d.getCategory().name();
            var uploaded = documentService.upload(
                    new DocumentUploadRequest(
                            d.getLabel() + " — " + subjectType + " " + subjectId,
                            "Généré depuis " + d.getCode() + " v" + rev.getVersion(),
                            typeDoc,
                            null,
                            null,
                            null,
                            subjectType,
                            subjectId,
                            null,
                            null
                    ),
                    new InMemoryMultipartFile("file", d.getCode() + "." + ext, mime, outBytes),
                    orgId,
                    userId
            );
            outputDocId = uploaded.id();
        }

        GeneratedDocument gd = new GeneratedDocument();
        gd.setOrganisationId(orgId);
        gd.setTemplateRevision(rev);
        gd.setSubjectType(subjectType);
        gd.setSubjectId(subjectId);
        gd.setOutputDocumentId(outputDocId);
        gd.setOutputFormat(outputFormat.name());
        gd.setCreatedBy(userId);
        gd = generatedDocumentRepository.save(gd);

        auditLogService.log(orgId, userId, "GENERATE_DOCUMENT", "GeneratedDocument", gd.getId(), null, Map.of(
                "templateCode", d.getCode(),
                "version", rev.getVersion(),
                "subjectType", subjectType,
                "subjectId", subjectId.toString(),
                "format", outputFormat.name()
        ));

        return toResp(gd);
    }

    private String fetchTemplateHtml(TemplateRevision rev, UUID orgId, UUID userId) throws Exception {
        if (rev.getContentDocumentId() == null) throw BusinessException.badRequest("TEMPLATE_CONTENU_ABSENT");
        String objectName = documentService.getObjectName(rev.getContentDocumentId(), orgId, userId);
        var dl = minioStorageService.download(objectName);
        try (var in = dl.stream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private byte[] fetchTemplateDocx(TemplateRevision rev, UUID orgId, UUID userId) throws Exception {
        if (rev.getContentDocumentId() == null) throw BusinessException.badRequest("TEMPLATE_CONTENU_ABSENT");
        String objectName = documentService.getObjectName(rev.getContentDocumentId(), orgId, userId);
        var dl = minioStorageService.download(objectName);
        try (var in = dl.stream()) {
            return in.readAllBytes();
        }
    }

    private TemplateDefinition loadOwned(UUID id, UUID orgId) {
        TemplateDefinition d = definitionRepository.findById(id).orElseThrow(() -> BusinessException.notFound("TEMPLATE_ABSENT"));
        if (!orgId.equals(d.getOrganisationId())) throw BusinessException.forbidden("TEMPLATE_ORG_MISMATCH");
        return d;
    }

    private TemplateDefinitionResponse toResp(TemplateDefinition d) {
        return new TemplateDefinitionResponse(
                d.getId(),
                d.getCode(),
                d.getLabel(),
                d.getCategory(),
                d.getFormat(),
                d.getStatus(),
                d.getDefaultLocale(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }

    private TemplateRevisionResponse toResp(TemplateRevision r) {
        return new TemplateRevisionResponse(
                r.getId(),
                r.getVersion(),
                r.getContentDocumentId(),
                r.getContentObjectName(),
                r.getContentMime(),
                r.getChecksum(),
                r.getComment(),
                r.getCreatedBy(),
                r.getCreatedAt()
        );
    }

    private GeneratedDocumentResponse toResp(GeneratedDocument gd) {
        return new GeneratedDocumentResponse(
                gd.getId(),
                gd.getTemplateRevision().getId(),
                gd.getSubjectType(),
                gd.getSubjectId(),
                gd.getOutputDocumentId(),
                gd.getOutputFormat(),
                gd.getCreatedBy(),
                gd.getCreatedAt()
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            return String.format("%064x", new BigInteger(1, digest));
        } catch (Exception e) {
            return null;
        }
    }
}

