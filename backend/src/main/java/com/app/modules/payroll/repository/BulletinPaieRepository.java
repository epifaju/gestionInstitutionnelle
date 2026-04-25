package com.app.modules.payroll.repository;

import com.app.modules.payroll.entity.BulletinPaie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BulletinPaieRepository extends JpaRepository<BulletinPaie, UUID> {
    Optional<BulletinPaie> findByOrganisationIdAndSalarie_IdAndAnneeAndMois(UUID organisationId, UUID salarieId, int annee, int mois);
}

