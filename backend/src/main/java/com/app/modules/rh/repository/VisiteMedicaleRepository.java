package com.app.modules.rh.repository;

import com.app.modules.rh.entity.VisiteMedicale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VisiteMedicaleRepository extends JpaRepository<VisiteMedicale, UUID> {

    List<VisiteMedicale> findBySalarieIdOrderByDateRealiseeDesc(UUID salarieId);

    @Query("""
            SELECT v FROM VisiteMedicale v
            WHERE v.prochaineVisite IS NOT NULL
              AND v.prochaineVisite <= :date
            """)
    List<VisiteMedicale> findVisitesProchainesAvant(@Param("date") LocalDate date);
}

