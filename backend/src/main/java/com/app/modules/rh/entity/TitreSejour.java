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

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "titres_sejour")
@EntityListeners(AuditListener.class)
@Getter
@Setter
public class TitreSejour extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "salarie_id", nullable = false)
    private Salarie salarie;

    @Column(name = "echeance_id")
    private UUID echeanceId;

    @Column(name = "type_document", nullable = false, length = 100)
    private String typeDocument;

    @Column(name = "numero_document", length = 100)
    private String numeroDocument;

    @Column(name = "pays_emetteur", length = 100)
    private String paysEmetteur;

    @Column(name = "date_emission")
    private LocalDate dateEmission;

    @Column(name = "date_expiration", nullable = false)
    private LocalDate dateExpiration;

    @Column(name = "autorite_emettrice", length = 200)
    private String autoriteEmettrice;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "statut_renouvellement", length = 50)
    private String statutRenouvellement = "NON_ENGAGE";

    @Column(columnDefinition = "text")
    private String notes;
}

