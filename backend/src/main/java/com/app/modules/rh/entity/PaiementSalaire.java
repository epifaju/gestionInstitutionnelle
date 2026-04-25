package com.app.modules.rh.entity;

import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "paiements_salaires")
@Getter
@Setter
public class PaiementSalaire extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "salarie_id", nullable = false)
    private Salarie salarie;

    @Column(nullable = false)
    private Integer mois;

    @Column(nullable = false)
    private Integer annee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 3)
    private String devise = "EUR";

    @Column(name = "date_paiement")
    private LocalDate datePaiement;

    @Column(name = "mode_paiement", length = 50)
    private String modePaiement;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "statut_paie")
    private StatutPaie statut = StatutPaie.EN_ATTENTE;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "bulletin_id")
    private UUID bulletinId;
}
