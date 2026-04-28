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

@Entity
@Table(name = "visites_medicales")
@EntityListeners(AuditListener.class)
@Getter
@Setter
public class VisiteMedicale extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "salarie_id", nullable = false)
    private Salarie salarie;

    @Column(name = "echeance_id")
    private java.util.UUID echeanceId;

    @Column(name = "type_visite", nullable = false, length = 100)
    private String typeVisite;

    @Column(name = "date_planifiee")
    private LocalDate datePlanifiee;

    @Column(name = "date_realisee")
    private LocalDate dateRealisee;

    @Column(length = 200)
    private String medecin;

    @Column(name = "centre_medical", length = 200)
    private String centreMedical;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "statut_visite")
    private StatutVisite statut = StatutVisite.PLANIFIEE;

    @Column(length = 50)
    private String resultat;

    @Column(columnDefinition = "text")
    private String restrictions;

    @Column(name = "prochaine_visite")
    private LocalDate prochaineVisite;

    @Column(name = "periodicite_mois", nullable = false)
    private Integer periodiciteMois = 24;

    @Column(name = "compte_rendu_url", length = 500)
    private String compteRenduUrl;

    @Column(columnDefinition = "text")
    private String notes;
}

