package com.app.modules.rh.repository;

import com.app.modules.rh.entity.TitreSejour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TitreSejourRepository extends JpaRepository<TitreSejour, UUID> {

    List<TitreSejour> findBySalarieIdOrderByDateExpirationAsc(UUID salarieId);

    @Query("""
            SELECT t FROM TitreSejour t
            WHERE t.organisationId = :orgId
              AND t.dateExpiration <= :date
            ORDER BY t.dateExpiration ASC
            """)
    List<TitreSejour> findExpirantAvant(@Param("orgId") UUID orgId, @Param("date") LocalDate date);
}

