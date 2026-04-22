package com.app.modules.finance.repository;

import com.app.modules.finance.entity.FacturePaiement;
import com.app.modules.finance.entity.FacturePaiementId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface FacturePaiementRepository extends JpaRepository<FacturePaiement, FacturePaiementId> {

    @Query("SELECT COALESCE(SUM(fp.montant), 0) FROM FacturePaiement fp WHERE fp.facture.id = :factureId")
    BigDecimal sumMontantByFactureId(@Param("factureId") UUID factureId);
}
