package com.app.modules.budget.entity;

import com.app.modules.auth.entity.Utilisateur;
import com.app.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "budgets_annuels")
@Getter
@Setter
public class BudgetAnnuel extends BaseEntity {

    @Column(nullable = false)
    private Integer annee;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "statut_budget")
    private StatutBudget statut = StatutBudget.BROUILLON;

    @Column(name = "date_validation")
    private Instant dateValidation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valideur_id")
    private Utilisateur valideur;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneBudget> lignes = new ArrayList<>();
}
