package com.app.modules.missions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
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
@Table(name = "frais_mission")
@Getter
@Setter
public class FraisMission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(name = "type_frais", nullable = false, length = 50)
    private String typeFrais;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "date_frais", nullable = false)
    private LocalDate dateFrais;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 3)
    private String devise = "EUR";

    @Column(name = "taux_change_eur", nullable = false, precision = 10, scale = 6)
    private BigDecimal tauxChangeEur = BigDecimal.ONE;

    @Column(name = "justificatif_url", length = 500)
    private String justificatifUrl;

    @Column(name = "justificatif_document_id")
    private UUID justificatifDocumentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "statut_frais")
    private StatutFrais statut = StatutFrais.BROUILLON;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public BigDecimal getMontantEur() {
        if (montant == null) return BigDecimal.ZERO;
        if (tauxChangeEur == null) return montant;
        return montant.multiply(tauxChangeEur);
    }
}

