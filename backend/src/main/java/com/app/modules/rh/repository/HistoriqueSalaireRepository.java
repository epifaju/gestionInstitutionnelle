package com.app.modules.rh.repository;

import com.app.modules.rh.entity.HistoriqueSalaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HistoriqueSalaireRepository extends JpaRepository<HistoriqueSalaire, UUID> {

    Optional<HistoriqueSalaire> findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(UUID salarieId);

    List<HistoriqueSalaire> findBySalarie_IdOrderByDateDebutDesc(UUID salarieId);

    @Query("select h from HistoriqueSalaire h where h.dateFin is not null and h.dateFin between :from and :to")
    List<HistoriqueSalaire> findEndingBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
