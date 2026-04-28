package com.app.modules.rh.repository;

import com.app.modules.rh.entity.EcheanceRh;
import com.app.modules.rh.entity.StatutEcheance;
import com.app.modules.rh.entity.TypeEcheance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EcheanceRhRepository extends JpaRepository<EcheanceRh, UUID> {

    Optional<EcheanceRh> findByIdAndOrganisationId(UUID id, UUID orgId);

    /**
     * Filtres optionnels sans {@code (:x IS NULL OR col = :x)} : sous PostgreSQL + enums JDBC,
     * cela provoquait {@code could not determine data type of parameter}.
     * {@code COALESCE(:param, expr)} garde le typage via la colonne / le chemin d'association.
     */
    @Query("""
            SELECT e FROM EcheanceRh e
            WHERE e.organisationId = :orgId
              AND COALESCE(:statut, e.statut) = e.statut
              AND COALESCE(:type, e.typeEcheance) = e.typeEcheance
              AND COALESCE(:salarieId, e.salarie.id) = e.salarie.id
              AND e.dateEcheance >= COALESCE(:dateMin, e.dateEcheance)
              AND e.dateEcheance <= COALESCE(:dateMax, e.dateEcheance)
            """)
    Page<EcheanceRh> findByOrganisationIdAndFilters(
            @Param("orgId") UUID orgId,
            @Param("statut") StatutEcheance statut,
            @Param("type") TypeEcheance type,
            @Param("salarieId") UUID salarieId,
            @Param("dateMin") LocalDate dateMin,
            @Param("dateMax") LocalDate dateMax,
            Pageable p
    );

    @Query("""
            SELECT e FROM EcheanceRh e
            WHERE e.dateAlerteJ30 <= :today
              AND e.rappelJ30Envoye = false
              AND e.statut NOT IN ('TRAITEE','ANNULEE','EXPIREE')
            """)
    List<EcheanceRh> findEcheancesAlerteJ30(@Param("today") LocalDate today);

    @Query("""
            SELECT e FROM EcheanceRh e
            WHERE e.dateAlerteJ7 <= :today
              AND e.rappelJ7Envoye = false
              AND e.statut NOT IN ('TRAITEE','ANNULEE','EXPIREE')
            """)
    List<EcheanceRh> findEcheancesAlerteJ7(@Param("today") LocalDate today);

    @Query("""
            SELECT e FROM EcheanceRh e
            WHERE e.dateEcheance < :today
              AND e.statut IN ('A_VENIR','EN_ALERTE','ACTION_REQUISE')
            """)
    List<EcheanceRh> findEcheancesExpirees(@Param("today") LocalDate today);

    long countByOrganisationIdAndStatutIn(UUID orgId, List<StatutEcheance> statuts);

    long countByOrganisationIdAndTypeEcheanceAndStatutNotIn(UUID orgId, TypeEcheance type, List<StatutEcheance> statuts);

    @Query("""
            SELECT e FROM EcheanceRh e
            WHERE e.organisationId = :orgId
              AND e.statut NOT IN ('TRAITEE','ANNULEE','EXPIREE')
            ORDER BY e.dateEcheance ASC
            """)
    List<EcheanceRh> findTopUrgentes(@Param("orgId") UUID orgId, Pageable p);

    boolean existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(
            UUID orgId, UUID salarieId, TypeEcheance typeEcheance, LocalDate dateEcheance, List<StatutEcheance> statuts
    );

    @Query("""
            SELECT e FROM EcheanceRh e
            WHERE e.organisationId = :orgId
              AND e.contratId = :contratId
              AND e.typeEcheance = :type
              AND e.statut NOT IN ('ANNULEE')
            ORDER BY e.createdAt DESC
            """)
    Optional<EcheanceRh> findByOrganisationIdAndContratIdAndType(@Param("orgId") UUID orgId, @Param("contratId") UUID contratId, @Param("type") TypeEcheance type);
}

