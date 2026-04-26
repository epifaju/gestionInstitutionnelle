package com.app.modules.missions.entity;

import com.app.modules.rh.entity.Salarie;
import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.UUID;

@Entity
@Table(name = "missions")
@Getter
@Setter
public class Mission extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "salarie_id", nullable = false)
    private Salarie salarie;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(nullable = false, length = 200)
    private String destination;

    @Column(name = "pays_destination", length = 100)
    private String paysDestination;

    @Column(columnDefinition = "text")
    private String objectif;

    @Column(name = "date_depart", nullable = false)
    private LocalDate dateDepart;

    @Column(name = "date_retour", nullable = false)
    private LocalDate dateRetour;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "statut_mission")
    private StatutMission statut = StatutMission.BROUILLON;

    @Column(name = "avance_demandee", precision = 12, scale = 2)
    private BigDecimal avanceDemandee = BigDecimal.ZERO;

    @Column(name = "avance_devise", length = 3)
    private String avanceDevise = "EUR";

    @Column(name = "avance_versee", precision = 12, scale = 2)
    private BigDecimal avanceVersee = BigDecimal.ZERO;

    @Column(name = "approbateur_id")
    private java.util.UUID approbateurId;

    @Column(name = "date_approbation")
    private Instant dateApprobation;

    @Column(name = "motif_refus", columnDefinition = "text")
    private String motifRefus;

    @Column(name = "ordre_mission_url", length = 500)
    private String ordreMissionUrl;

    @Column(name = "ordre_mission_document_id")
    private UUID ordreMissionDocumentId;

    @Column(name = "rapport_url", length = 500)
    private String rapportUrl;

    @Column(name = "rapport_document_id")
    private UUID rapportDocumentId;

    @PreUpdate
    void onPreUpdate() {
        setUpdatedAt(Instant.now());
    }
}

