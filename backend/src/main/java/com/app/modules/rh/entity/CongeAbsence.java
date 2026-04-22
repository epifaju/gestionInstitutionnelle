package com.app.modules.rh.entity;

import com.app.modules.auth.entity.Utilisateur;
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

@Entity
@Table(name = "conges_absences")
@Getter
@Setter
public class CongeAbsence extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "salarie_id", nullable = false)
    private Salarie salarie;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type_conge", nullable = false, columnDefinition = "type_conge")
    private TypeConge typeConge;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "nb_jours", nullable = false, precision = 5, scale = 1)
    private BigDecimal nbJours;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "statut_conge")
    private StatutConge statut = StatutConge.BROUILLON;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valideur_id")
    private Utilisateur valideur;

    @Column(name = "date_validation")
    private Instant dateValidation;

    @Column(name = "motif_rejet", columnDefinition = "text")
    private String motifRejet;

    @Column(columnDefinition = "text")
    private String commentaire;

    @PreUpdate
    void onPreUpdate() {
        setUpdatedAt(Instant.now());
    }
}
