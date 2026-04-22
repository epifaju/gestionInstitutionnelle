package com.app.modules.finance.repository;

import com.app.modules.finance.entity.TauxChange;
import com.app.modules.finance.entity.TauxChangeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TauxChangeRepository extends JpaRepository<TauxChange, TauxChangeId> {
    Optional<TauxChange> findByOrganisationIdAndDateAndDevise(UUID organisationId, LocalDate date, String devise);
    List<TauxChange> findAllByOrganisationIdAndDate(UUID organisationId, LocalDate date);
}

