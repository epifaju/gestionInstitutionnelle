package com.app.modules.rapports.entity;

import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "export_jobs")
@Getter
@Setter
public class ExportJob extends BaseEntity {

    @Column(name = "demande_par", nullable = false)
    private UUID demandePar;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type_export", nullable = false, columnDefinition = "type_export")
    private TypeExport typeExport;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> parametres;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "statut_export_job")
    private StatutExportJob statut = StatutExportJob.EN_ATTENTE;

    @Column(nullable = false)
    private int progression = 0;

    @Column(name = "message_erreur", columnDefinition = "text")
    private String messageErreur;

    @Column(name = "fichier_url", length = 500)
    private String fichierUrl;

    @Column(name = "nom_fichier", length = 300)
    private String nomFichier;

    @Column(name = "taille_octets")
    private Long tailleOctets;

    @Column(name = "nb_lignes")
    private Integer nbLignes;

    @Column(name = "expire_a", insertable = false, updatable = false)
    private Instant expireA;
}

