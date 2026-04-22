package com.app.modules.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "facture_paiements")
@IdClass(FacturePaiementId.class)
@Getter
@Setter
public class FacturePaiement {

    @Id
    @Column(name = "facture_id", nullable = false)
    private UUID factureId;

    @Id
    @Column(name = "paiement_id", nullable = false)
    private UUID paiementId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facture_id", nullable = false, insertable = false, updatable = false)
    private Facture facture;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paiement_id", nullable = false, insertable = false, updatable = false)
    private Paiement paiement;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;
}
