package com.app.modules.auth.entity;

import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "utilisateurs")
@Getter
@Setter
public class Utilisateur extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "role_enum")
    private Role role = Role.EMPLOYE;

    @Column(length = 100)
    private String nom;

    @Column(length = 100)
    private String prenom;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "dernier_login")
    private Instant dernierLogin;

    /**
     * Langue UI préférée (ex: fr, en, pt-PT).
     * Stockée sur le profil utilisateur.
     */
    @Column(nullable = false, length = 10)
    private String langue = "fr";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String preferences = "{}";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", insertable = false, updatable = false)
    private Organisation organisation;
}
