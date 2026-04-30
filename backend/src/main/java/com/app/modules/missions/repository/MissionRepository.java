package com.app.modules.missions.repository;

import com.app.modules.missions.entity.Mission;
import com.app.modules.missions.entity.StatutMission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface MissionRepository extends JpaRepository<Mission, UUID>, JpaSpecificationExecutor<Mission> {

    long countByOrganisationIdAndStatut(UUID organisationId, StatutMission statut);

    Page<Mission> findByOrganisationIdAndStatutOrderByCreatedAtAsc(UUID organisationId, StatutMission statut, Pageable pageable);
}

