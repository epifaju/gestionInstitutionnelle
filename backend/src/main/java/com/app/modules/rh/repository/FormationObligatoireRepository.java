package com.app.modules.rh.repository;

import com.app.modules.rh.entity.FormationObligatoire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FormationObligatoireRepository extends JpaRepository<FormationObligatoire, UUID> {

    List<FormationObligatoire> findBySalarieIdOrderByDateExpirationAsc(UUID salarieId);

    @Query("""
            SELECT f FROM FormationObligatoire f
            WHERE f.organisationId = :orgId
              AND f.dateExpiration <= :date
            ORDER BY f.dateExpiration ASC
            """)
    List<FormationObligatoire> findExpirantAvant(@Param("orgId") UUID orgId, @Param("date") LocalDate date);

    @Query("""
            SELECT f FROM FormationObligatoire f
            WHERE f.dateExpiration < :date
              AND f.statut <> 'EXPIREE'
            """)
    List<FormationObligatoire> findToutesExpireesAvant(@Param("date") LocalDate date);
}

