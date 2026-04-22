package com.app.modules.inventaire.repository;

import com.app.modules.inventaire.entity.BienMateriel;
import com.app.modules.inventaire.entity.EtatBien;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class BienMaterielSpecifications {

    private BienMaterielSpecifications() {}

    public static Specification<BienMateriel> organisationIdEq(UUID orgId) {
        return (root, q, cb) -> cb.equal(root.get("organisationId"), orgId);
    }

    public static Specification<BienMateriel> categorieContains(String categorie) {
        if (categorie == null || categorie.isBlank()) {
            return null;
        }
        String pattern = "%" + categorie.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("categorie")), pattern);
    }

    public static Specification<BienMateriel> etatEq(EtatBien etat) {
        if (etat == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("etat"), etat);
    }

    public static Specification<BienMateriel> localisationContains(String localisation) {
        if (localisation == null || localisation.isBlank()) {
            return null;
        }
        String pattern = "%" + localisation.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("localisation")), pattern);
    }

    public static Specification<BienMateriel> and(Specification<BienMateriel> base, Specification<BienMateriel> add) {
        if (add == null) {
            return base;
        }
        return base.and(add);
    }
}
