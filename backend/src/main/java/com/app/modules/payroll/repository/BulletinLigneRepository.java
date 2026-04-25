package com.app.modules.payroll.repository;

import com.app.modules.payroll.entity.BulletinLigne;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BulletinLigneRepository extends JpaRepository<BulletinLigne, UUID> {
    List<BulletinLigne> findByBulletin_IdOrderByOrdreAffichageAsc(UUID bulletinId);
}

