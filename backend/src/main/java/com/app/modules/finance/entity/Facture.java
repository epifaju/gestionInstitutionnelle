package com.app.modules.finance.entity;

import com.app.audit.AuditListener;
import com.app.modules.auth.entity.Utilisateur;
import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "factures")
@EntityListeners(AuditListener.class)
@Getter
@Setter
public class Facture extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String reference;

    @Column(nullable = false, length = 200)
    private String fournisseur;

    @Column(name = "date_facture", nullable = false)
    private LocalDate dateFacture;

    @Column(name = "montant_ht", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantHt;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal tva = BigDecimal.ZERO;

    @Column(name = "montant_ttc", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantTtc;

    @Column(nullable = false, length = 3)
    private String devise = "EUR";

    @Column(name = "taux_change_eur", nullable = false, precision = 10, scale = 6)
    private BigDecimal tauxChangeEur = BigDecimal.ONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private CategorieDepense categorie;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "statut_facture")
    private StatutFacture statut = StatutFacture.BROUILLON;

    @Column(name = "justificatif_url", length = 500)
    private String justificatifUrl;

    @Column(columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Utilisateur createdBy;

    @PreUpdate
    void onPreUpdate() {
        setUpdatedAt(Instant.now());
    }
}
