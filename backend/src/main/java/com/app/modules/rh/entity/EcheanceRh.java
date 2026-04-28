package com.app.modules.rh.entity;

import com.app.audit.AuditListener;
import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "echeances_rh")
@EntityListeners(AuditListener.class)
@Getter
@Setter
public class EcheanceRh extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "salarie_id", nullable = false)
    private Salarie salarie;

    @Column(name = "contrat_id")
    private UUID contratId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type_echeance", nullable = false, columnDefinition = "type_echeance")
    private TypeEcheance typeEcheance;

    @Column(nullable = false, length = 300)
    private String titre;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "date_echeance", nullable = false)
    private LocalDate dateEcheance;

    @Column(name = "date_alerte_j30", insertable = false, updatable = false)
    private LocalDate dateAlerteJ30;

    @Column(name = "date_alerte_j7", insertable = false, updatable = false)
    private LocalDate dateAlerteJ7;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "statut_echeance")
    private StatutEcheance statut = StatutEcheance.A_VENIR;

    @Column(nullable = false)
    private Integer priorite = 2;

    @Column(name = "responsable_id")
    private UUID responsableId;

    @Column(name = "date_traitement")
    private LocalDate dateTraitement;

    @Column(name = "commentaire_traitement", columnDefinition = "text")
    private String commentaireTraitement;

    @Column(name = "traite_par")
    private UUID traitePar;

    @Column(name = "document_preuve_url", length = 500)
    private String documentPreuveUrl;

    @Column(name = "rappel_j30_envoye", nullable = false)
    private boolean rappelJ30Envoye = false;

    @Column(name = "rappel_j7_envoye", nullable = false)
    private boolean rappelJ7Envoye = false;

    @Column(name = "rappel_j0_envoye", nullable = false)
    private boolean rappelJ0Envoye = false;

    @Column(name = "created_by")
    private UUID createdBy;
}

