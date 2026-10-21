package com.app.modules.courriers.repository;

import com.app.modules.courriers.entity.CanalCourrier;
import com.app.modules.courriers.entity.Courrier;
import com.app.modules.courriers.entity.SensCourrier;
import com.app.modules.courriers.entity.StatutCourrier;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public final class CourrierSpecifications {

    private CourrierSpecifications() {}

    public static Specification<Courrier> organisationId(UUID orgId) {
        return (root, q, cb) -> cb.equal(root.get("organisationId"), orgId);
    }

    public static Specification<Courrier> sensOptional(SensCourrier sens) {
        return (root, q, cb) -> sens == null ? cb.conjunction() : cb.equal(root.get("sens"), sens);
    }

    public static Specification<Courrier> statutOptional(StatutCourrier statut) {
        return (root, q, cb) -> statut == null ? cb.conjunction() : cb.equal(root.get("statut"), statut);
    }

    public static Specification<Courrier> canalOptional(CanalCourrier canal) {
        return (root, q, cb) -> canal == null ? cb.conjunction() : cb.equal(root.get("canal"), canal);
    }

    public static Specification<Courrier> dateFromOptional(LocalDate from) {
        return (root, q, cb) ->
                from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("dateCourrier"), from);
    }

    public static Specification<Courrier> dateToOptional(LocalDate to) {
        return (root, q, cb) ->
                to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("dateCourrier"), to);
    }

    public static Specification<Courrier> queryOptional(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) return cb.conjunction();
            String like = "%" + q.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("objet")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("reference"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("expediteur"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("destinataire"), "")), like));
        };
    }
}
