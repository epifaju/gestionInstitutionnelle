package com.app.modules.rh.entity;

import com.app.audit.AuditListener;
import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "formations_obligatoires")
@EntityListeners(AuditListener.class)
@Getter
@Setter
public class FormationObligatoire extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "salarie_id", nullable = false)
    private Salarie salarie;

    @Column(name = "echeance_id")
    private UUID echeanceId;

    @Column(nullable = false, length = 300)
    private String intitule;

    @Column(name = "type_formation", nullable = false, length = 100)
    private String typeFormation;

    @Column(length = 200)
    private String organisme;

    @Column(name = "date_realisation")
    private LocalDate dateRealisation;

    @Column(name = "date_expiration", nullable = false)
    private LocalDate dateExpiration;

    @Column(name = "periodicite_mois")
    private Integer periodiciteMois;

    @Column(name = "numero_certificat", length = 100)
    private String numeroCertificat;

    @Column(name = "certificat_url", length = 500)
    private String certificatUrl;

    @Column(nullable = false, length = 50)
    private String statut = "A_REALISER";

    @Column(precision = 10, scale = 2)
    private BigDecimal cout;

    @Column(columnDefinition = "text")
    private String notes;
}

