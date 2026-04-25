package com.app.modules.missions.repository;

import com.app.modules.missions.entity.Mission;
import com.app.modules.missions.entity.StatutMission;
import com.app.modules.rh.entity.Salarie;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public final class MissionSpecifications {

    private MissionSpecifications() {}

    public static Specification<Mission> organisationId(UUID orgId) {
        return (root, q, cb) -> cb.equal(root.get("organisationId"), orgId);
    }

    public static Specification<Mission> statutOptional(StatutMission statut) {
        return (root, q, cb) -> statut == null ? cb.conjunction() : cb.equal(root.get("statut"), statut);
    }

    public static Specification<Mission> salarieOptional(UUID salarieId) {
        return (root, q, cb) -> {
            if (salarieId == null) return cb.conjunction();
            Join<Mission, Salarie> sal = root.join("salarie", JoinType.INNER);
            return cb.equal(sal.get("id"), salarieId);
        };
    }

    public static Specification<Mission> dateDebutOptional(LocalDate debut) {
        return (root, q, cb) ->
                debut == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("dateRetour"), debut);
    }

    public static Specification<Mission> dateFinOptional(LocalDate fin) {
        return (root, q, cb) ->
                fin == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("dateDepart"), fin);
    }
}

