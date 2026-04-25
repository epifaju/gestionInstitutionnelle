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
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bulletins_paie")
@Getter
@Setter
public class BulletinPaie extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "salarie_id", nullable = false)
    private Salarie salarie;

    @Column(nullable = false)
    private Integer annee;

    @Column(nullable = false)
    private Integer mois;

    @Column(name = "date_paiement", nullable = false)
    private LocalDate datePaiement;

    @Column(nullable = false, length = 3)
    private String devise = "EUR";

    @Column(nullable = false)
    private boolean cadre = false;

    @Column(name = "convention_code", length = 50)
    private String conventionCode;

    @Column(name = "convention_libelle", length = 200)
    private String conventionLibelle;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal brut = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "net_imposable", nullable = false, precision = 12, scale = 2)
    private BigDecimal netImposable = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "pas_taux", precision = 6, scale = 4)
    private BigDecimal pasTaux;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "pas_montant", nullable = false, precision = 12, scale = 2)
    private BigDecimal pasMontant = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "net_a_payer", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAPayer = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "total_cot_sal", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCotSal = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "total_cot_pat", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCotPat = BigDecimal.ZERO;

    @Column(name = "pdf_object_name", length = 500)
    private String pdfObjectName;

    @Column(name = "pdf_generated_at")
    private Instant pdfGeneratedAt;
}

