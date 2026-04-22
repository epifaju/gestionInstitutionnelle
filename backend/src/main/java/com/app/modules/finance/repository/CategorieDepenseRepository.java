package com.app.modules.finance.repository;

import com.app.modules.finance.entity.CategorieDepense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategorieDepenseRepository extends JpaRepository<CategorieDepense, UUID> {

    Optional<CategorieDepense> findByIdAndOrganisationId(UUID id, UUID organisationId);

    List<CategorieDepense> findByOrganisationIdAndActifTrueOrderByLibelleAsc(UUID organisationId);

    List<CategorieDepense> findByOrganisationIdOrderByLibelleAsc(UUID organisationId);

    boolean existsByOrganisationIdAndCode(UUID organisationId, String code);
}
