package com.app.modules.finance.repository;

import com.app.modules.finance.entity.CategorieDepense;
import com.app.modules.finance.entity.Facture;
import com.app.modules.finance.entity.StatutFacture;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtres optionnels sans expression {@code (:p IS NULL OR ...)} : avec PostgreSQL + enums JDBC,
 * les paramètres {@code NULL} non typés provoquent {@code could not determine data type of parameter}.
 */
public final class FactureSpecifications {

    private FactureSpecifications() {}

    public static Specification<Facture> organisationId(UUID orgId) {
        return (root, q, cb) -> cb.equal(root.get("organisationId"), orgId);
    }

    public static Specification<Facture> statutOptional(StatutFacture statut) {
        return (root, q, cb) -> statut == null ? cb.conjunction() : cb.equal(root.get("statut"), statut);
    }

    public static Specification<Facture> categorieIdOptional(UUID categorieId) {
        return (root, q, cb) -> {
            if (categorieId == null) {
                return cb.conjunction();
            }
            Join<Facture, CategorieDepense> cat = root.join("categorie", JoinType.INNER);
            return cb.equal(cat.get("id"), categorieId);
        };
    }

    public static Specification<Facture> dateDebutOptional(LocalDate debut) {
        return (root, q, cb) ->
                debut == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("dateFacture"), debut);
    }

    public static Specification<Facture> dateFinOptional(LocalDate fin) {
        return (root, q, cb) ->
                fin == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("dateFacture"), fin);
    }

    public static Specification<Facture> fournisseurOptional(String fournisseur) {
        return (root, q, cb) -> {
            if (fournisseur == null || fournisseur.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + fournisseur.trim().toLowerCase() + "%";
            return cb.like(cb.lower(root.get("fournisseur")), pattern);
        };
    }

    public static Specification<Facture> montantMinOptional(BigDecimal montantMin) {
        return (root, q, cb) ->
                montantMin == null ? cb.conjunction() : cb.ge(root.get("montantTtc"), montantMin);
    }

    public static Specification<Facture> montantMaxOptional(BigDecimal montantMax) {
        return (root, q, cb) ->
                montantMax == null ? cb.conjunction() : cb.le(root.get("montantTtc"), montantMax);
    }
}
