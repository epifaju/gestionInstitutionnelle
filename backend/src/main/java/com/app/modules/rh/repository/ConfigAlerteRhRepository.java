package com.app.modules.rh.repository;

import com.app.modules.rh.entity.ConfigAlerteRh;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConfigAlerteRhRepository extends JpaRepository<ConfigAlerteRh, UUID> {

    Optional<ConfigAlerteRh> findByOrganisationId(UUID organisationId);
}

