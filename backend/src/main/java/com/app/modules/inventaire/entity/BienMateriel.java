package com.app.modules.inventaire.entity;

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

@Entity
@Table(name = "biens_materiels")
@Getter
@Setter
public class BienMateriel extends BaseEntity {

    @Column(name = "code_inventaire", nullable = false, length = 30)
    private String codeInventaire;

    @Column(nullable = false, length = 200)
    private String libelle;

    @Column(nullable = false, length = 100)
    private String categorie;

    @Column(name = "code_categorie", nullable = false, length = 10)
    private String codeCategorie;

    @Column(name = "date_acquisition")
    private LocalDate dateAcquisition;

    @Column(name = "valeur_achat", nullable = false, precision = 12, scale = 2)
    private BigDecimal valeurAchat = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String devise = "EUR";

    @Column(length = 200)
    private String localisation;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "etat_bien")
    private EtatBien etat = EtatBien.BON;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id")
    private Salarie responsable;

    @Column(columnDefinition = "TEXT")
    private String description;

    @PreUpdate
    void onPreUpdate() {
        setUpdatedAt(Instant.now());
    }
}
