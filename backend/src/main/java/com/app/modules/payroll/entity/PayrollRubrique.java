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
@Table(name = "payroll_rubriques")
@Getter
@Setter
public class PayrollRubrique extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(nullable = false, length = 20)
    private String type; // GAIN, RETENUE, INFO

    @Column(name = "mode_calcul", nullable = false, length = 30)
    private String modeCalcul; // FIXED, PERCENT_BASE

    @Column(name = "base_code", length = 50)
    private String baseCode;

    @Column(name = "taux_salarial", precision = 10, scale = 6)
    private BigDecimal tauxSalarial;

    @Column(name = "taux_patronal", precision = 10, scale = 6)
    private BigDecimal tauxPatronal;

    @Column(name = "montant_fixe", precision = 12, scale = 2)
    private BigDecimal montantFixe;

    @Column(name = "ordre_affichage", nullable = false)
    private Integer ordreAffichage = 100;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}

