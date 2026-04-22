package com.app.modules.auth.entity;

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
import java.util.UUID;

@Entity
@Table(name = "organisations")
@Getter
@Setter
public class Organisation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(length = 100)
    private String pays;

    @Column(name = "devise_defaut", nullable = false, length = 3)
    private String deviseDefaut = "EUR";

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "seuil_justificatif", nullable = false)
    private BigDecimal seuilJustificatif = new BigDecimal("500.00");

    @Column(name = "alerte_budget_pct", nullable = false)
    private Integer alerteBudgetPct = 80;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
