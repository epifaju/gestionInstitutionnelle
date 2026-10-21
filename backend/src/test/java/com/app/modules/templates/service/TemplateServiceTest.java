package com.app.modules.templates.service;

import com.app.audit.AuditLogService;
import com.app.modules.ged.dto.DocumentResponse;
import com.app.modules.ged.service.DocumentService;
import com.app.modules.templates.dto.CreateTemplateRequest;
import com.app.modules.templates.dto.UpdateTemplateRequest;
import com.app.modules.templates.entity.GeneratedDocument;
import com.app.modules.templates.entity.TemplateCategory;
import com.app.modules.templates.entity.TemplateDefinition;
import com.app.modules.templates.entity.TemplateFormat;
import com.app.modules.templates.entity.TemplateRevision;
import com.app.modules.templates.entity.TemplateStatus;
import com.app.modules.templates.repository.GeneratedDocumentRepository;
import com.app.modules.templates.repository.TemplateDefinitionRepository;
import com.app.modules.templates.repository.TemplateRevisionRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID TPL_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID REV_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID DOC_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("f0000000-0000-0000-0000-000000000001");

    @Mock private TemplateDefinitionRepository definitionRepository;
    @Mock private TemplateRevisionRepository revisionRepository;
    @Mock private GeneratedDocumentRepository generatedDocumentRepository;
    @Mock private TemplateRenderService renderService;
    @Mock private TemplateDataService dataService;
    @Mock private DocumentService documentService;
    @Mock private AuditLogService auditLogService;
    @Mock private MinioStorageService minioStorageService;

    private TemplateService svc;

    @BeforeEach
    void setUp() {
        svc = new TemplateService(
                definitionRepository,
                revisionRepository,
                generatedDocumentRepository,
                renderService,
                dataService,
                documentService,
                auditLogService,
                minioStorageService);
    }

    @Test
    void create_refuseCodeDejaExistant() {
        when(definitionRepository.findByOrganisationIdAndCode(ORG_ID, "CODE"))
                .thenReturn(Optional.of(new TemplateDefinition()));

        assertThatThrownBy(() -> svc.create(
                        new CreateTemplateRequest("code", "Label", TemplateCategory.MISSION, TemplateFormat.DOCX, "fr"),
                        ORG_ID,
                        USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_CODE_DEJA_EXISTANT");
    }

    @Test
    void create_ok_normaliseCode() {
        when(definitionRepository.findByOrganisationIdAndCode(ORG_ID, "CODE")).thenReturn(Optional.empty());
        when(definitionRepository.save(any(TemplateDefinition.class))).thenAnswer(inv -> {
            TemplateDefinition d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        var resp = svc.create(
                new CreateTemplateRequest(" code ", "Label", TemplateCategory.MISSION, TemplateFormat.DOCX, "fr"),
                ORG_ID,
                USER_ID);
        assertThat(resp.code()).isEqualTo("CODE");
        verify(auditLogService).log(eq(ORG_ID), eq(USER_ID), eq("CREATE_TEMPLATE"), eq("TemplateDefinition"), any(), eq(null), any());
    }

    @Test
    void list_filtreParOrganisation() {
        TemplateDefinition mine = ownedDef(TemplateFormat.DOCX);
        TemplateDefinition other = new TemplateDefinition();
        other.setId(UUID.randomUUID());
        other.setOrganisationId(UUID.randomUUID());
        other.setCode("OTHER");
        other.setLabel("Other");
        other.setCategory(TemplateCategory.MISSION);
        other.setFormat(TemplateFormat.DOCX);
        other.setStatus(TemplateStatus.ACTIVE);
        when(definitionRepository.findAll()).thenReturn(List.of(mine, other));

        assertThat(svc.list(ORG_ID)).hasSize(1).first().extracting("code").isEqualTo("MISSION_ORDRE");
    }

    @Test
    void update_ok() {
        TemplateDefinition d = ownedDef(TemplateFormat.DOCX);
        when(definitionRepository.findById(TPL_ID)).thenReturn(Optional.of(d));
        when(definitionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = svc.update(TPL_ID, new UpdateTemplateRequest("Nouveau", TemplateStatus.ACTIVE, "en"), ORG_ID, USER_ID);
        assertThat(resp.label()).isEqualTo("Nouveau");
        assertThat(resp.status()).isEqualTo(TemplateStatus.ACTIVE);
        assertThat(resp.defaultLocale()).isEqualTo("en");
    }

    @Test
    void update_refuseOrgMismatch() {
        TemplateDefinition d = ownedDef(TemplateFormat.DOCX);
        d.setOrganisationId(UUID.randomUUID());
        when(definitionRepository.findById(TPL_ID)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> svc.update(TPL_ID, new UpdateTemplateRequest("X", TemplateStatus.DRAFT, "fr"), ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_ORG_MISMATCH");
    }

    @Test
    void listRevisions_ok() {
        TemplateDefinition d = ownedDef(TemplateFormat.DOCX);
        when(definitionRepository.findById(TPL_ID)).thenReturn(Optional.of(d));
        TemplateRevision r = revision(d, 1);
        when(revisionRepository.findByTemplate_IdOrderByVersionDesc(TPL_ID)).thenReturn(List.of(r));

        assertThat(svc.listRevisions(TPL_ID, ORG_ID)).hasSize(1).first().extracting("version").isEqualTo(1);
    }

    @Test
    void addRevision_refuseFichierAbsent() {
        when(definitionRepository.findById(TPL_ID)).thenReturn(Optional.of(ownedDef(TemplateFormat.DOCX)));

        assertThatThrownBy(() -> svc.addRevision(TPL_ID, null, "c", ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_ABSENT");
    }

    @Test
    void addRevision_ok_premiereVersion() throws Exception {
        TemplateDefinition d = ownedDef(TemplateFormat.DOCX);
        when(definitionRepository.findById(TPL_ID)).thenReturn(Optional.of(d));
        when(revisionRepository.findTopByTemplate_IdOrderByVersionDesc(TPL_ID)).thenReturn(Optional.empty());
        when(documentService.upload(any(), any(), eq(ORG_ID), eq(USER_ID))).thenReturn(docResp());
        when(revisionRepository.save(any(TemplateRevision.class))).thenAnswer(inv -> {
            TemplateRevision r = inv.getArgument(0);
            r.setId(REV_ID);
            return r;
        });

        MockMultipartFile file = new MockMultipartFile("file", "tpl.docx", "application/octet-stream", "docx-bytes".getBytes());
        var resp = svc.addRevision(TPL_ID, file, "v1", ORG_ID, USER_ID);

        assertThat(resp.version()).isEqualTo(1);
        assertThat(resp.contentDocumentId()).isEqualTo(DOC_ID);
        assertThat(resp.checksum()).isNotBlank();
        verify(auditLogService).log(eq(ORG_ID), eq(USER_ID), eq("ADD_TEMPLATE_REVISION"), eq("TemplateRevision"), eq(REV_ID), eq(null), any());
    }

    @Test
    void listGenerated_ok() {
        TemplateRevision rev = revision(ownedDef(TemplateFormat.DOCX), 1);
        GeneratedDocument gd = new GeneratedDocument();
        gd.setId(UUID.randomUUID());
        gd.setTemplateRevision(rev);
        gd.setSubjectType("MISSION");
        gd.setSubjectId(SUBJECT_ID);
        gd.setOutputFormat("DOCX");
        gd.setCreatedBy(USER_ID);
        gd.setCreatedAt(Instant.now());
        when(generatedDocumentRepository.findByOrganisationIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                        ORG_ID, "MISSION", SUBJECT_ID))
                .thenReturn(List.of(gd));

        assertThat(svc.listGenerated(ORG_ID, "MISSION", SUBJECT_ID)).hasSize(1);
    }

    @Test
    void generate_refuseTemplateAbsent() {
        when(definitionRepository.findByOrganisationIdAndCode(ORG_ID, "ABSENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.generate(
                        "absent", "MISSION", SUBJECT_ID, TemplateOutputFormat.DOCX, false, null, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_ABSENT");
    }

    @Test
    void generate_refuseRevisionAbsente() {
        when(definitionRepository.findByOrganisationIdAndCode(ORG_ID, "MISSION_ORDRE"))
                .thenReturn(Optional.of(ownedDef(TemplateFormat.DOCX)));
        when(revisionRepository.findTopByTemplate_IdOrderByVersionDesc(TPL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.generate(
                        "MISSION_ORDRE", "MISSION", SUBJECT_ID, TemplateOutputFormat.DOCX, false, null, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_REVISION_ABSENTE");
    }

    @Test
    void generate_htmlRefusePdfOutput() {
        TemplateDefinition d = ownedDef(TemplateFormat.HTML);
        TemplateRevision rev = revision(d, 1);
        when(definitionRepository.findByOrganisationIdAndCode(ORG_ID, "MISSION_ORDRE")).thenReturn(Optional.of(d));
        when(revisionRepository.findTopByTemplate_IdOrderByVersionDesc(TPL_ID)).thenReturn(Optional.of(rev));
        when(dataService.buildValues("MISSION", SUBJECT_ID, ORG_ID)).thenReturn(Map.of("x", "1"));

        assertThatThrownBy(() -> svc.generate(
                        "MISSION_ORDRE", "MISSION", SUBJECT_ID, TemplateOutputFormat.PDF, false, null, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_FORMAT_INVALIDE");
    }

    @Test
    void generate_docxSansGed_ok() throws Exception {
        TemplateDefinition d = ownedDef(TemplateFormat.DOCX);
        TemplateRevision rev = revision(d, 2);
        when(definitionRepository.findByOrganisationIdAndCode(ORG_ID, "MISSION_ORDRE")).thenReturn(Optional.of(d));
        when(revisionRepository.findTopByTemplate_IdOrderByVersionDesc(TPL_ID)).thenReturn(Optional.of(rev));
        when(dataService.buildValues("MISSION", SUBJECT_ID, ORG_ID))
                .thenReturn(new java.util.HashMap<>(Map.of("nom", "Alice")));
        when(documentService.getObjectName(DOC_ID, ORG_ID, USER_ID)).thenReturn("templates/tpl.docx");
        when(minioStorageService.download("templates/tpl.docx"))
                .thenReturn(new MinioStorageService.Download(
                        "templates/tpl.docx",
                        "application/octet-stream",
                        4,
                        new ByteArrayInputStream("docx".getBytes())));
        when(renderService.renderDocx(any(), any())).thenReturn("rendered".getBytes());
        when(generatedDocumentRepository.save(any(GeneratedDocument.class))).thenAnswer(inv -> {
            GeneratedDocument gd = inv.getArgument(0);
            gd.setId(UUID.randomUUID());
            return gd;
        });

        var resp = svc.generate(
                "MISSION_ORDRE",
                "MISSION",
                SUBJECT_ID,
                TemplateOutputFormat.DOCX,
                false,
                Map.of("extra", "v"),
                ORG_ID,
                USER_ID);

        assertThat(resp.subjectType()).isEqualTo("MISSION");
        assertThat(resp.outputDocumentId()).isNull();
        assertThat(resp.outputFormat()).isEqualTo("DOCX");
        verify(renderService).renderDocx(any(), eq(Map.of("nom", "Alice", "extra", "v")));
    }

    @Test
    void generate_htmlAvecGed_ok() throws Exception {
        TemplateDefinition d = ownedDef(TemplateFormat.HTML);
        TemplateRevision rev = revision(d, 1);
        when(definitionRepository.findByOrganisationIdAndCode(ORG_ID, "MISSION_ORDRE")).thenReturn(Optional.of(d));
        when(revisionRepository.findTopByTemplate_IdOrderByVersionDesc(TPL_ID)).thenReturn(Optional.of(rev));
        when(dataService.buildValues("MISSION", SUBJECT_ID, ORG_ID)).thenReturn(new java.util.HashMap<>());
        when(documentService.getObjectName(DOC_ID, ORG_ID, USER_ID)).thenReturn("templates/tpl.html");
        when(minioStorageService.download("templates/tpl.html"))
                .thenReturn(new MinioStorageService.Download(
                        "templates/tpl.html",
                        "text/html",
                        12,
                        new ByteArrayInputStream("<p>{{x}}</p>".getBytes())));
        when(renderService.renderHtml(any(), any())).thenReturn("<p>ok</p>".getBytes());
        when(documentService.upload(any(), any(), eq(ORG_ID), eq(USER_ID))).thenReturn(docResp());
        when(generatedDocumentRepository.save(any(GeneratedDocument.class))).thenAnswer(inv -> {
            GeneratedDocument gd = inv.getArgument(0);
            gd.setId(UUID.randomUUID());
            return gd;
        });

        var resp = svc.generate(
                "MISSION_ORDRE", "MISSION", SUBJECT_ID, TemplateOutputFormat.HTML, true, null, ORG_ID, USER_ID);

        assertThat(resp.outputDocumentId()).isEqualTo(DOC_ID);
        assertThat(resp.outputFormat()).isEqualTo("HTML");
    }

    private TemplateDefinition ownedDef(TemplateFormat format) {
        TemplateDefinition d = new TemplateDefinition();
        d.setId(TPL_ID);
        d.setOrganisationId(ORG_ID);
        d.setCode("MISSION_ORDRE");
        d.setLabel("Ordre de mission");
        d.setCategory(TemplateCategory.MISSION);
        d.setFormat(format);
        d.setStatus(TemplateStatus.DRAFT);
        d.setDefaultLocale("fr");
        d.setCreatedAt(Instant.now());
        d.setUpdatedAt(Instant.now());
        return d;
    }

    private TemplateRevision revision(TemplateDefinition d, int version) {
        TemplateRevision r = new TemplateRevision();
        r.setId(REV_ID);
        r.setTemplate(d);
        r.setVersion(version);
        r.setContentDocumentId(DOC_ID);
        r.setContentMime("application/octet-stream");
        r.setChecksum("abc");
        r.setCreatedBy(USER_ID);
        r.setCreatedAt(Instant.now());
        return r;
    }

    private static DocumentResponse docResp() {
        return new DocumentResponse(
                DOC_ID,
                "tpl",
                null,
                "TEMPLATE_MISSION",
                null,
                "tpl.docx",
                10L,
                "application/octet-stream",
                1,
                null,
                "PRIVE",
                null,
                null,
                null,
                null,
                "Admin",
                LocalDateTime.now(),
                null);
    }
}
