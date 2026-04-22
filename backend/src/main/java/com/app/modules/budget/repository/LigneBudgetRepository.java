package com.app.modules.budget.repository;

import com.app.modules.budget.entity.LigneBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LigneBudgetRepository extends JpaRepository<LigneBudget, UUID> {

    Optional<LigneBudget> findByIdAndBudget_Id(UUID ligneId, UUID budgetId);
}
