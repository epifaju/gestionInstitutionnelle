package com.app.modules.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "taux_change_historique")
@Getter
@Setter
public class TauxChangeHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "devise_base", nullable = false, length = 3)
    private String deviseBase = "EUR";

    @Column(name = "devise_cible", nullable = false, length = 3)
    private String deviseCible;

    @Column(name = "taux", nullable = false, precision = 12, scale = 6)
    private BigDecimal taux;

    @Column(name = "date_taux", nullable = false)
    private LocalDate dateTaux;

    @Column(name = "source", nullable = false, length = 50)
    private String source = "API";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

