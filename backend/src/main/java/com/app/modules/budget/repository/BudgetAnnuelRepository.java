package com.app.modules.budget.repository;

import com.app.modules.budget.entity.BudgetAnnuel;
import com.app.modules.budget.entity.StatutBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetAnnuelRepository extends JpaRepository<BudgetAnnuel, UUID> {

    Optional<BudgetAnnuel> findByOrganisationIdAndAnneeAndStatut(UUID organisationId, Integer annee, StatutBudget statut);

    List<BudgetAnnuel> findByOrganisationIdAndAnnee(UUID organisationId, Integer annee);

    boolean existsByOrganisationIdAndAnneeAndStatutIn(
            UUID organisationId, Integer annee, Iterable<StatutBudget> statuts);
}
