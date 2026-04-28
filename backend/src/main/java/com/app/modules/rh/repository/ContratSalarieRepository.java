package com.app.modules.rh.repository;

import com.app.modules.rh.entity.ContratSalarie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContratSalarieRepository extends JpaRepository<ContratSalarie, UUID> {

    Optional<ContratSalarie> findByIdAndOrganisationId(UUID id, UUID orgId);

    @Query("""
            SELECT c FROM ContratSalarie c
            WHERE c.salarie.id = :salarieId AND c.actif = true
            """)
    Optional<ContratSalarie> findBySalarieIdAndActifTrue(@Param("salarieId") UUID salarieId);

    @Query("""
            SELECT c FROM ContratSalarie c
            WHERE c.organisationId = :orgId
              AND c.typeContrat = :typeContrat
              AND c.actif = true
            """)
    Page<ContratSalarie> findByOrganisationIdAndTypeContratAndActifTrue(
            @Param("orgId") UUID orgId,
            @Param("typeContrat") String typeContrat,
            Pageable p
    );

    @Query("""
            SELECT c FROM ContratSalarie c
            WHERE c.organisationId = :orgId
              AND c.typeContrat = 'CDD' AND c.actif = true
              AND c.dateFinContrat BETWEEN :dateMin AND :dateMax
            """)
    List<ContratSalarie> findCddExpirantDans(@Param("orgId") UUID orgId, @Param("dateMin") LocalDate dateMin, @Param("dateMax") LocalDate dateMax);

    @Query("""
            SELECT c FROM ContratSalarie c
            WHERE c.organisationId = :orgId
              AND c.dateFinPeriodeEssai BETWEEN :dateMin AND :dateMax
              AND c.actif = true
            """)
    List<ContratSalarie> findPeriodeEssaiExpirantDans(@Param("orgId") UUID orgId, @Param("dateMin") LocalDate dateMin, @Param("dateMax") LocalDate dateMax);

    @Query("""
            SELECT c FROM ContratSalarie c
            WHERE c.organisationId = :orgId
              AND c.actif = true
              AND (:typeContrat IS NULL OR c.typeContrat = :typeContrat)
              AND (:service IS NULL OR c.salarie.service = :service)
            """)
    Page<ContratSalarie> listActifs(@Param("orgId") UUID orgId, @Param("typeContrat") String typeContrat, @Param("service") String service, Pageable p);

    @Query("""
            SELECT c FROM ContratSalarie c
            WHERE c.salarie.id = :salarieId
            ORDER BY c.createdAt DESC
            """)
    List<ContratSalarie> findBySalarieIdOrderByCreatedAtDesc(@Param("salarieId") UUID salarieId);
}

