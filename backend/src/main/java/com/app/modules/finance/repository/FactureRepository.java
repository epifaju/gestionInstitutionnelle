package com.app.modules.finance.repository;

import com.app.modules.finance.entity.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface FactureRepository extends JpaRepository<Facture, UUID>, JpaSpecificationExecutor<Facture> {}
