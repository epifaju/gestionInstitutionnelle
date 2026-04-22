package com.app.modules.rh.entity;

import com.app.modules.auth.entity.Utilisateur;
import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "salaries")
@Getter
@Setter
public class Salarie extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @Column(nullable = false, length = 50)
    private String matricule;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String telephone;

    @Column(nullable = false, length = 150)
    private String poste;

    @Column(nullable = false, length = 150)
    private String service;

    @Column(name = "date_embauche", nullable = false)
    private LocalDate dateEmbauche;

    @Column(name = "type_contrat", nullable = false, length = 50)
    private String typeContrat;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "statut_salarie")
    private StatutSalarie statut = StatutSalarie.BROUILLON;

    @Column(length = 100)
    private String nationalite;

    @Column(columnDefinition = "text")
    private String adresse;

    @PreUpdate
    void onPreUpdate() {
        setUpdatedAt(Instant.now());
    }

    public UUID getUtilisateurId() {
        return utilisateur == null ? null : utilisateur.getId();
    }
}
