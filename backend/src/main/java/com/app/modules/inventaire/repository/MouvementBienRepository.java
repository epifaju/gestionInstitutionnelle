package com.app.modules.inventaire.repository;

import com.app.modules.inventaire.entity.MouvementBien;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MouvementBienRepository extends JpaRepository<MouvementBien, UUID> {

    @EntityGraph(attributePaths = "auteur")
    List<MouvementBien> findByBien_IdOrderByDateMouvementDesc(UUID bienId);
}
