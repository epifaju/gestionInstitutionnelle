package com.app.modules.payroll.entity;

import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payroll_cotisations")
@Getter
@Setter
public class PayrollCotisation extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(length = 100)
    private String organisme;

    @Column(name = "assiette_base_code", nullable = false, length = 50)
    private String assietteBaseCode;

    @Column(name = "taux_salarial", precision = 10, scale = 6)
    private BigDecimal tauxSalarial;

    @Column(name = "taux_patronal", precision = 10, scale = 6)
    private BigDecimal tauxPatronal;

    @Column(name = "plafond_code", length = 50)
    private String plafondCode;

    @Column(name = "applies_cadre_only", nullable = false)
    private boolean appliesCadreOnly = false;

    @Column(name = "applies_non_cadre_only", nullable = false)
    private boolean appliesNonCadreOnly = false;

    @Column(name = "ordre_affichage", nullable = false)
    private Integer ordreAffichage = 100;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}

