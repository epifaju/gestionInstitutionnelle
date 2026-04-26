package com.app.modules.ged.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class DocumentAccesId implements Serializable {

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;
}

