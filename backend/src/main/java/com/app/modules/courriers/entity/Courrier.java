package com.app.modules.courriers.entity;

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

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "courriers")
@Getter
@Setter
public class Courrier extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "sens_courrier")
    private SensCourrier sens;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "statut_courrier")
    private StatutCourrier statut = StatutCourrier.BROUILLON;

    @Column(length = 100)
    private String reference;

    @Column(nullable = false, length = 300)
    private String objet;

    @Column(length = 300)
    private String expediteur;

    @Column(length = 300)
    private String destinataire;

    @Column(name = "date_courrier", nullable = false)
    private LocalDate dateCourrier;

    @Column(name = "date_reception")
    private LocalDate dateReception;

    @Column(name = "date_envoi")
    private LocalDate dateEnvoi;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "canal_courrier")
    private CanalCourrier canal = CanalCourrier.COURRIER_POSTAL;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "created_by")
    private UUID createdBy;
}
