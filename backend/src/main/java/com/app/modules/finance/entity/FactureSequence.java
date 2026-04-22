package com.app.modules.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "facture_sequences")
@Getter
@Setter
public class FactureSequence {

    @EmbeddedId
    private FactureSequenceId id;

    @Column(name = "derniere_seq", nullable = false)
    private Integer derniereSeq = 0;
}
