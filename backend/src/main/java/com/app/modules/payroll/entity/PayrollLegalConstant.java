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
@Table(name = "payroll_legal_constants")
@Getter
@Setter
public class PayrollLegalConstant extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal valeur;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}

