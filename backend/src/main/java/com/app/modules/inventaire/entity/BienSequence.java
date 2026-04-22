package com.app.modules.inventaire.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "bien_sequences")
@IdClass(BienSequenceId.class)
@Getter
@Setter
public class BienSequence {

    @Id
    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Id
    @Column(name = "code_categorie", nullable = false, length = 10)
    private String codeCategorie;

    @Id
    @Column(nullable = false)
    private Integer annee;

    @Column(name = "derniere_seq", nullable = false)
    private Integer derniereSeq = 0;
}
