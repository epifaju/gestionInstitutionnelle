package com.app.modules.rh.repository;

import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutSalarie;
import org.springframework.data.jpa.domain.Specification;

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

    public static Specification<Salarie> searchOptional(String search) {
        return (root, q, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            var n = cb.coalesce(root.get("nom"), "");
            var p = cb.coalesce(root.get("prenom"), "");
            var m = cb.coalesce(root.get("matricule"), "");
            var step = cb.concat(cb.concat(cb.concat(n, cb.literal(" ")), p), cb.literal(" "));
            var full = cb.concat(step, m);
            return cb.like(cb.lower(full), pattern);
        };
    }
}
