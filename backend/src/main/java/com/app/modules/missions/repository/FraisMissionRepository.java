package com.app.modules.missions.repository;

import com.app.modules.missions.entity.FraisMission;
import com.app.modules.missions.entity.StatutFrais;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FraisMissionRepository extends JpaRepository<FraisMission, UUID> {

    List<FraisMission> findByMission_IdOrderByDateFraisDescCreatedAtDesc(UUID missionId);

    Optional<FraisMission> findByIdAndMission_Id(UUID id, UUID missionId);

    List<FraisMission> findByMission_IdAndStatut(UUID missionId, StatutFrais statut);
}

