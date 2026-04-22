package com.app.modules.rh.repository;

import com.app.modules.rh.entity.PaiementSalaire;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaiementSalaireRepository extends JpaRepository<PaiementSalaire, UUID> {

    boolean existsBySalarie_IdAndMoisAndAnnee(UUID salarieId, int mois, int annee);

    Page<PaiementSalaire> findBySalarie_IdAndAnneeOrderByMoisAsc(UUID salarieId, int annee, Pageable pageable);

    List<PaiementSalaire> findBySalarie_IdAndAnneeOrderByMoisAsc(UUID salarieId, int annee);

    Page<PaiementSalaire> findByOrganisationIdAndAnneeOrderBySalarie_NomAscSalarie_PrenomAscMoisAsc(
            UUID organisationId, int annee, Pageable pageable);
}
