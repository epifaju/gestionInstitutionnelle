package com.app.modules.templates.service;

import com.app.audit.AuditLogService;
import com.app.modules.ged.service.DocumentService;
import com.app.modules.templates.dto.CreateTemplateRequest;
import com.app.modules.templates.entity.TemplateCategory;
import com.app.modules.templates.entity.TemplateDefinition;
import com.app.modules.templates.entity.TemplateFormat;
import com.app.modules.templates.repository.GeneratedDocumentRepository;
import com.app.modules.templates.repository.TemplateDefinitionRepository;
import com.app.modules.templates.repository.TemplateRevisionRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Mock
    private TemplateDefinitionRepository definitionRepository;
    @Mock
    private TemplateRevisionRepository revisionRepository;
    @Mock
    private GeneratedDocumentRepository generatedDocumentRepository;
    @Mock
    private TemplateRenderService renderService;
    @Mock
    private TemplateDataService dataService;
    @Mock
    private DocumentService documentService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private MinioStorageService minioStorageService;

    @Test
    void create_refuseCodeDejaExistant() {
        when(definitionRepository.findByOrganisationIdAndCode(orgId, "CODE")).thenReturn(Optional.of(new TemplateDefinition()));
        TemplateService svc = new TemplateService(
                definitionRepository,
                revisionRepository,
                generatedDocumentRepository,
                renderService,
                dataService,
                documentService,
                auditLogService,
                minioStorageService);

        assertThatThrownBy(() -> svc.create(new CreateTemplateRequest("code", "Label", TemplateCategory.MISSION, TemplateFormat.DOCX, "fr"), orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_CODE_DEJA_EXISTANT");
    }

    @Test
    void create_ok_normaliseCode() {
        when(definitionRepository.findByOrganisationIdAndCode(orgId, "CODE")).thenReturn(Optional.empty());
        when(definitionRepository.save(any(TemplateDefinition.class))).thenAnswer(inv -> {
            TemplateDefinition d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        TemplateService svc = new TemplateService(
                definitionRepository,
                revisionRepository,
                generatedDocumentRepository,
                renderService,
                dataService,
                documentService,
                auditLogService,
                minioStorageService);

        var resp = svc.create(new CreateTemplateRequest(" code ", "Label", TemplateCategory.MISSION, TemplateFormat.DOCX, "fr"), orgId, userId);
        assertThat(resp.code()).isEqualTo("CODE");
    }
}

