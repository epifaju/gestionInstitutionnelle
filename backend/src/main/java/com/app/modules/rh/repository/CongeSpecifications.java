package com.app.modules.rh.repository;

import com.app.modules.rh.entity.CongeAbsence;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutConge;
import com.app.modules.rh.entity.TypeConge;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Remplace la JPQL avec {@code (:p IS NULL OR ...)} (échec PostgreSQL 42P18 sur enums / NULL JDBC).
 */
public final class CongeSpecifications {

    private CongeSpecifications() {}

    public static Specification<CongeAbsence> organisationId(UUID orgId) {
        return (root, q, cb) -> cb.equal(root.get("organisationId"), orgId);
    }

    public static Specification<CongeAbsence> statutOptional(StatutConge statut) {
        return (root, q, cb) -> statut == null ? cb.conjunction() : cb.equal(root.get("statut"), statut);
    }

    public static Specification<CongeAbsence> typeCongeOptional(TypeConge type) {
        return (root, q, cb) -> type == null ? cb.conjunction() : cb.equal(root.get("typeConge"), type);
    }

    public static Specification<CongeAbsence> dateDebutFilter(LocalDate debut) {
        return (root, q, cb) -> debut == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("dateFin"), debut);
    }

    public static Specification<CongeAbsence> dateFinFilter(LocalDate fin) {
        return (root, q, cb) -> fin == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("dateDebut"), fin);
    }

    public static Specification<CongeAbsence> salarieJoinFilters(String service, UUID salarieId) {
        return (root, q, cb) -> {
            boolean hasSvc = service != null && !service.isBlank();
            boolean hasSid = salarieId != null;
            if (!hasSvc && !hasSid) {
                return cb.conjunction();
            }
            Join<CongeAbsence, Salarie> sal = root.join("salarie", JoinType.INNER);
            return cb.and(
                    hasSid ? cb.equal(sal.get("id"), salarieId) : cb.conjunction(),
                    hasSvc ? cb.equal(sal.get("service"), service.trim()) : cb.conjunction());
        };
    }
}
