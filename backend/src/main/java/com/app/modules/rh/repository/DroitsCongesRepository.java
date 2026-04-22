package com.app.modules.rh.repository;

import com.app.modules.rh.entity.DroitsConges;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DroitsCongesRepository extends JpaRepository<DroitsConges, UUID> {

    Optional<DroitsConges> findBySalarie_IdAndAnnee(UUID salarieId, int annee);
}
