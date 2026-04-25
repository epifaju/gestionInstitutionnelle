package com.app.modules.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payroll_employer_settings")
@Getter
@Setter
public class PayrollEmployerSettings {

    @Id
    @Column(name = "organisation_id")
    private UUID organisationId;

    @Column(name = "raison_sociale", nullable = false, length = 200)
    private String raisonSociale;

    @Column(name = "adresse_ligne1", length = 200)
    private String adresseLigne1;

    @Column(name = "adresse_ligne2", length = 200)
    private String adresseLigne2;

    @Column(name = "code_postal", length = 20)
    private String codePostal;

    @Column(length = 100)
    private String ville;

    @Column(length = 100)
    private String pays;

    @Column(length = 20)
    private String siret;

    @Column(length = 10)
    private String naf;

    @Column(length = 100)
    private String urssaf;

    @Column(name = "convention_code", length = 50)
    private String conventionCode;

    @Column(name = "convention_libelle", length = 200)
    private String conventionLibelle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

