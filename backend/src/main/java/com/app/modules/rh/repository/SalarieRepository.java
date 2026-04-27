package com.app.modules.rh.repository;

import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutSalarie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalarieRepository extends JpaRepository<Salarie, UUID>, JpaSpecificationExecutor<Salarie> {

    long countByOrganisationId(UUID organisationId);

    List<Salarie> findByOrganisationIdAndStatut(UUID organisationId, StatutSalarie statut);

    Optional<Salarie> findByOrganisationIdAndUtilisateur_Id(UUID organisationId, UUID utilisateurId);

    Optional<Salarie> findByOrganisationIdAndEmailIgnoreCase(UUID organisationId, String email);

    Optional<Salarie> findByIdAndOrganisationId(UUID id, UUID organisationId);
}
