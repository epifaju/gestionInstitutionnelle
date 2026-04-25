package com.app.modules.finance.repository;

import com.app.modules.finance.entity.Facture;
import com.app.modules.finance.entity.StatutFacture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FactureRepository extends JpaRepository<Facture, UUID>, JpaSpecificationExecutor<Facture> {

    List<Facture> findByStatutAndDateFactureBefore(StatutFacture statut, LocalDate before);
}
