package com.app.modules.templates.repository;

import com.app.modules.templates.entity.TemplateCategory;
import com.app.modules.templates.entity.TemplateDefinition;
import com.app.modules.templates.entity.TemplateFormat;
import com.app.modules.templates.entity.TemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateDefinitionRepository extends JpaRepository<TemplateDefinition, UUID> {
    Optional<TemplateDefinition> findByOrganisationIdAndCode(UUID organisationId, String code);

    List<TemplateDefinition> findByOrganisationIdAndCategoryAndFormatAndStatus(
            UUID organisationId, TemplateCategory category, TemplateFormat format, TemplateStatus status);

    List<TemplateDefinition> findByOrganisationIdAndCategoryAndStatus(
            UUID organisationId, TemplateCategory category, TemplateStatus status
    );
}

