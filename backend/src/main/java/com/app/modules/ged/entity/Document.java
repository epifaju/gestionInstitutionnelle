package com.app.modules.ged.entity;

import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document extends BaseEntity {

    @Column(nullable = false, length = 300)
    private String titre;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "type_document", nullable = false, length = 100)
    private String typeDocument;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] tags;

    @Column(name = "fichier_url", nullable = false, length = 500)
    private String fichierUrl;

    @Column(name = "nom_fichier", nullable = false, length = 300)
    private String nomFichier;

    @Column(name = "taille_octets", nullable = false)
    private long tailleOctets;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(nullable = false)
    private int version = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_parent_id")
    private Document documentParent;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "visibilite_doc")
    private VisibiliteDoc visibilite = VisibiliteDoc.ORGANISATION;

    @Column(name = "service_cible", length = 150)
    private String serviceCible;

    @Column(name = "entite_liee_type", length = 100)
    private String entiteLieeType;

    @Column(name = "entite_liee_id")
    private java.util.UUID entiteLieeId;

    @Column(name = "date_expiration")
    private LocalDate dateExpiration;

    @Column(name = "supprime", nullable = false)
    private boolean supprime = false;

    @Column(name = "date_suppression")
    private Instant dateSuppression;

    @Column(name = "uploade_par")
    private java.util.UUID uploadePar;
}

