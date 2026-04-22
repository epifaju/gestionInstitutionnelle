package com.app.modules.rh.repository;

import com.app.modules.rh.entity.CongeAbsence;
import com.app.modules.rh.entity.StatutConge;
import com.app.modules.rh.entity.TypeConge;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CongeRepository extends JpaRepository<CongeAbsence, UUID>, JpaSpecificationExecutor<CongeAbsence> {

    @Query(
            """
                    SELECT COUNT(c) > 0 FROM CongeAbsence c
                    WHERE c.salarie.id = :salarieId
                    AND c.statut = :statutValide
                    AND c.dateDebut <= :fin
                    AND c.dateFin >= :debut
                    """)
    boolean existsChevauchement(
            @Param("salarieId") UUID salarieId,
            @Param("debut") LocalDate debut,
            @Param("fin") LocalDate fin,
            @Param("statutValide") StatutConge statutValide);

    @Query(
            """
                    SELECT c FROM CongeAbsence c
                    JOIN c.salarie s
                    WHERE c.organisationId = :orgId
                    AND c.dateDebut <= :fin
                    AND c.dateFin >= :debut
                    ORDER BY c.dateDebut ASC
                    """)
    List<CongeAbsence> findCalendrier(
            @Param("orgId") UUID orgId, @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query(
            """
                    SELECT c FROM CongeAbsence c
                    WHERE c.organisationId = :orgId
                    AND c.salarie.id = :salarieId
                    AND c.dateDebut <= :fin
                    AND c.dateFin >= :debut
                    ORDER BY c.dateDebut ASC
                    """)
    List<CongeAbsence> findCalendrierSalarie(
            @Param("orgId") UUID orgId, @Param("salarieId") UUID salarieId, @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /**
     * Somme des jours de congés validés consommant le solde (annuel / exceptionnel) dont la date de début
     * tombe dans l'intervalle [deb, fin] (typiquement une année civile).
     */
    @Query(
            """
                    SELECT COALESCE(SUM(c.nbJours), 0) FROM CongeAbsence c
                    WHERE c.salarie.id = :sid
                    AND c.statut = :stValide
                    AND (c.typeConge = :tAnnuel OR c.typeConge = :tExcep)
                    AND c.dateDebut >= :deb AND c.dateDebut <= :fin
                    """)
    BigDecimal sumJoursValidesSoldeEntreDates(
            @Param("sid") UUID salarieId,
            @Param("stValide") StatutConge statutValide,
            @Param("tAnnuel") TypeConge typeAnnuel,
            @Param("tExcep") TypeConge typeExceptionnel,
            @Param("deb") LocalDate debut,
            @Param("fin") LocalDate fin);
}
