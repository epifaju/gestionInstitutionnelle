package com.app.modules.rapports.repository;

import com.app.modules.rapports.entity.ExportJob;
import com.app.modules.rapports.entity.StatutExportJob;
import com.app.modules.rapports.entity.TypeExport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {

    Page<ExportJob> findByOrganisationIdOrderByCreatedAtDesc(UUID orgId, Pageable p);

    Optional<ExportJob> findByIdAndOrganisationId(UUID id, UUID orgId);

    boolean existsByOrganisationIdAndTypeExportAndStatutIn(
            UUID orgId, TypeExport type, List<StatutExportJob> statuts);

    List<ExportJob> findByStatutAndExpireABefore(StatutExportJob statut, Instant now);

    Page<ExportJob> findByOrganisationIdAndDemandeParOrderByCreatedAtDesc(UUID orgId, UUID demandePar, Pageable p);
}

