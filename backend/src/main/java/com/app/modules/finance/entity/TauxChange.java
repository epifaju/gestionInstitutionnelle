package com.app.modules.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "taux_change")
@IdClass(TauxChangeId.class)
@Getter
@Setter
public class TauxChange {

    @Id
    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Id
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Id
    @Column(name = "devise", nullable = false, length = 3)
    private String devise;

    @Column(name = "taux_vers_eur", nullable = false, precision = 12, scale = 6)
    private BigDecimal tauxVersEur;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

