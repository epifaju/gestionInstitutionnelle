package com.app.modules.inventaire.repository;

import com.app.modules.inventaire.entity.BienMateriel;
import com.app.modules.inventaire.entity.EtatBien;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface BienMaterielRepository extends JpaRepository<BienMateriel, UUID>, JpaSpecificationExecutor<BienMateriel> {

    @EntityGraph(attributePaths = "responsable")
    @Override
    Page<BienMateriel> findAll(Specification<BienMateriel> spec, Pageable pageable);

    @EntityGraph(attributePaths = "responsable")
    Optional<BienMateriel> findByIdAndOrganisationId(UUID id, UUID organisationId);

    long countByOrganisationIdAndEtat(UUID organisationId, EtatBien etat);

    java.util.List<BienMateriel> findTop5ByOrganisationIdAndEtatOrderByUpdatedAtDesc(UUID organisationId, EtatBien etat);
}
