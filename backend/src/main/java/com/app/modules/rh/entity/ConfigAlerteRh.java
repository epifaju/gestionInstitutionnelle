package com.app.modules.rh.entity;

import com.app.audit.AuditListener;
import com.app.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "config_alertes_rh")
@EntityListeners(AuditListener.class)
@Getter
@Setter
public class ConfigAlerteRh extends BaseEntity {

    @Column(name = "alerte_fin_cdd_j", nullable = false)
    private Integer alerteFinCddJ = 60;

    @Column(name = "alerte_periode_essai_j", nullable = false)
    private Integer alertePeriodeEssaiJ = 15;

    @Column(name = "alerte_visite_med_j", nullable = false)
    private Integer alerteVisiteMedJ = 30;

    @Column(name = "alerte_titre_sejour_j", nullable = false)
    private Integer alerteTitreSejourJ = 90;

    @Column(name = "alerte_formation_j", nullable = false)
    private Integer alerteFormationJ = 60;

    @Column(name = "notifier_rh", nullable = false)
    private boolean notifierRh = true;

    @Column(name = "notifier_manager", nullable = false)
    private boolean notifierManager = false;

    @Column(name = "notifier_salarie", nullable = false)
    private boolean notifierSalarie = false;

    @Column(name = "max_renouvellements_cdd", nullable = false)
    private Integer maxRenouvellementsCdd = 2;
}

