package com.app.modules.courriers.repository;

import com.app.modules.courriers.entity.Courrier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CourrierRepository extends JpaRepository<Courrier, UUID>, JpaSpecificationExecutor<Courrier> {

    Optional<Courrier> findByIdAndOrganisationId(UUID id, UUID organisationId);

    boolean existsByOrganisationIdAndReferenceIgnoreCase(UUID organisationId, String reference);

    boolean existsByOrganisationIdAndReferenceIgnoreCaseAndIdNot(UUID organisationId, String reference, UUID id);
}
