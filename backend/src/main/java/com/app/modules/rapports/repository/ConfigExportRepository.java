package com.app.modules.rapports.repository;

import com.app.modules.rapports.entity.ConfigExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConfigExportRepository extends JpaRepository<ConfigExport, UUID> {
    Optional<ConfigExport> findByOrganisationId(UUID orgId);
}

