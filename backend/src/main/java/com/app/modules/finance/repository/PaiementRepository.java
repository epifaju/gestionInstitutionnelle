package com.app.modules.finance.repository;

import com.app.modules.finance.entity.Paiement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaiementRepository extends JpaRepository<Paiement, UUID> {

    Page<Paiement> findByOrganisationIdOrderByDatePaiementDescCreatedAtDesc(UUID organisationId, Pageable pageable);
}
