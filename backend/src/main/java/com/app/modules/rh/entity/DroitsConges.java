package com.app.modules.rh.entity;

import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "droits_conges")
@Getter
@Setter
public class DroitsConges extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "salarie_id", nullable = false)
    private Salarie salarie;

    @Column(nullable = false)
    private Integer annee;

    @Column(name = "jours_droit", nullable = false, precision = 5, scale = 1)
    private BigDecimal joursDroit = new BigDecimal("30");

    @Column(name = "jours_pris", nullable = false, precision = 5, scale = 1)
    private BigDecimal joursPris = BigDecimal.ZERO;

    @Column(name = "jours_restants", nullable = false, precision = 5, scale = 1)
    private BigDecimal joursRestants = new BigDecimal("30");
}
