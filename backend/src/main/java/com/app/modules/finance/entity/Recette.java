package com.app.modules.finance.entity;

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
@Table(name = "recettes")
@Getter
@Setter
public class Recette extends BaseEntity {

    @Column(name = "date_recette", nullable = false)
    private LocalDate dateRecette;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 3)
    private String devise = "EUR";

    @Column(name = "taux_change_eur", nullable = false, precision = 10, scale = 6)
    private BigDecimal tauxChangeEur = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type_recette", nullable = false, columnDefinition = "type_recette")
    private TypeRecette typeRecette;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "mode_encaissement", length = 50)
    private String modeEncaissement;

    @Column(name = "justificatif_url", length = 500)
    private String justificatifUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private CategorieDepense categorie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Utilisateur createdBy;

    @PreUpdate
    void onPreUpdate() {
        setUpdatedAt(Instant.now());
    }
}
