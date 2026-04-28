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
@Table(name = "contrats_salaries")
@EntityListeners(AuditListener.class)
@Getter
@Setter
public class ContratSalarie extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "salarie_id", nullable = false)
    private Salarie salarie;

    @Column(name = "type_contrat", nullable = false, length = 50)
    private String typeContrat;

    @Column(name = "date_debut_contrat", nullable = false)
    private LocalDate dateDebutContrat;

    @Column(name = "date_fin_contrat")
    private LocalDate dateFinContrat;

    @Column(name = "date_fin_periode_essai")
    private LocalDate dateFinPeriodeEssai;

    @Column(name = "duree_essai_mois")
    private Integer dureeEssaiMois;

    @Column(name = "numero_contrat", length = 100)
    private String numeroContrat;

    @Column(name = "intitule_poste", length = 200)
    private String intitulePoste;

    @Column(name = "motif_cdd", columnDefinition = "text")
    private String motifCdd;

    @Column(name = "convention_collective", length = 200)
    private String conventionCollective;

    @Column(name = "renouvellement_numero", nullable = false)
    private Integer renouvellementNumero = 0;

    @Column(name = "contrat_parent_id")
    private UUID contratParentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "decision_fin", nullable = false, columnDefinition = "decision_fin_cdd")
    private DecisionFinCdd decisionFin = DecisionFinCdd.EN_ATTENTE;

    @Column(name = "date_decision")
    private LocalDate dateDecision;

    @Column(name = "commentaire_decision", columnDefinition = "text")
    private String commentaireDecision;

    @Column(name = "contrat_signe_url", length = 500)
    private String contratSigneUrl;

    @Column(name = "avenant_url", length = 500)
    private String avenantUrl;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "created_by")
    private UUID createdBy;
}

