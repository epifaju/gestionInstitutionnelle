package com.app.modules.rh.repository;

import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutSalarie;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

/**
 * Évite les prédicats {@code (:p IS NULL OR ...)} qui font échouer PostgreSQL sur les enums / NULL JDBC.
 */
public final class SalarieSpecifications {

    private SalarieSpecifications() {}

    public static Specification<Salarie> organisationId(UUID orgId) {
        return (root, q, cb) -> cb.equal(root.get("organisationId"), orgId);
    }

    public static Specification<Salarie> statutOptional(StatutSalarie statut) {
        return (root, q, cb) -> statut == null ? cb.conjunction() : cb.equal(root.get("statut"), statut);
    }

    public static Specification<Salarie> serviceOptional(String service) {
        return (root, q, cb) -> {
            if (service == null || service.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(root.get("service"), service.trim());
        };
    }

    /**
     * Recherche insensible à la casse sur chaque champ (nom, prénom, matricule, email).
     * L’ancienne version concaténait tout en une chaîne : certains dialectes / NULL JDBC rendaient
     * le prédicat fragile ; une saisie partielle (ex. prénom seul) est plus naturelle avec des OR.
     */
    public static Specification<Salarie> searchOptional(String search) {
        return (root, q, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String term = search.trim();
            if (term.isEmpty()) {
                return cb.conjunction();
            }
            String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
            Predicate nom = cb.like(cb.lower(cb.coalesce(root.get("nom"), cb.literal(""))), pattern, '\\');
            Predicate prenom = cb.like(cb.lower(cb.coalesce(root.get("prenom"), cb.literal(""))), pattern, '\\');
            Predicate matricule = cb.like(cb.lower(cb.coalesce(root.get("matricule"), cb.literal(""))), pattern, '\\');
            Predicate email = cb.like(cb.lower(cb.coalesce(root.get("email"), cb.literal(""))), pattern, '\\');
            return cb.or(nom, prenom, matricule, email);
        };
    }

    private static String escapeLike(String lowercasedTerm) {
        return lowercasedTerm.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
