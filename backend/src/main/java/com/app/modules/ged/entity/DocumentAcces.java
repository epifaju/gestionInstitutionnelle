package com.app.modules.ged.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "document_acces")
@Getter
@Setter
public class DocumentAcces {

    @EmbeddedId
    private DocumentAccesId id;

    @Column(name = "peut_modifier", nullable = false)
    private boolean peutModifier = false;

    @Column(name = "peut_supprimer", nullable = false)
    private boolean peutSupprimer = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

