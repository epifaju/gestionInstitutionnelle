package com.app.modules.ged.repository;

import com.app.modules.ged.entity.DocumentAcces;
import com.app.modules.ged.entity.DocumentAccesId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentAccesRepository extends JpaRepository<DocumentAcces, DocumentAccesId> {
    Optional<DocumentAcces> findById_DocumentIdAndId_UtilisateurId(UUID documentId, UUID utilisateurId);
    List<DocumentAcces> findAllById_DocumentId(UUID documentId);
}

