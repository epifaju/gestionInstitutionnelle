package com.app.modules.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bulletin_lignes")
@Getter
@Setter
public class BulletinLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "bulletin_id", nullable = false)
    private BulletinPaie bulletin;

    @Column(nullable = false, length = 30)
    private String section; // REMUNERATION, COTISATIONS, IMPOT, NET, INFO

    @Column(length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String libelle;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(precision = 12, scale = 2)
    private BigDecimal base;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "taux_salarial", precision = 10, scale = 6)
    private BigDecimal tauxSalarial;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "montant_salarial", precision = 12, scale = 2)
    private BigDecimal montantSalarial;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "taux_patronal", precision = 10, scale = 6)
    private BigDecimal tauxPatronal;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "montant_patronal", precision = 12, scale = 2)
    private BigDecimal montantPatronal;

    @Column(name = "ordre_affichage", nullable = false)
    private Integer ordreAffichage = 100;
}

