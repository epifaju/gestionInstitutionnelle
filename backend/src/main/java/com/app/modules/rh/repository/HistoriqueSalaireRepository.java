package com.app.modules.rh.repository;

import com.app.modules.rh.entity.HistoriqueSalaire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HistoriqueSalaireRepository extends JpaRepository<HistoriqueSalaire, UUID> {

    Optional<HistoriqueSalaire> findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(UUID salarieId);

    List<HistoriqueSalaire> findBySalarie_IdOrderByDateDebutDesc(UUID salarieId);
}
