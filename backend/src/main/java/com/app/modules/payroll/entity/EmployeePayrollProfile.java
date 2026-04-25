package com.app.modules.payroll.entity;

import com.app.modules.rh.entity.Salarie;
import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "employee_payroll_profile")
@Getter
@Setter
public class EmployeePayrollProfile extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "salarie_id", nullable = false)
    private Salarie salarie;

    @Column(nullable = false)
    private boolean cadre = false;

    @Column(name = "convention_code", length = 50)
    private String conventionCode;

    @Column(name = "convention_libelle", length = 200)
    private String conventionLibelle;

    /**
     * Taux PAS stocké en fraction (ex: 0.0730 pour 7.30%).
     */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "taux_pas", precision = 6, scale = 4)
    private BigDecimal tauxPas;
}

