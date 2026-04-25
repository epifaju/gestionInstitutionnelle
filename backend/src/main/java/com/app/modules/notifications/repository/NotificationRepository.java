package com.app.modules.notifications.repository;

import com.app.modules.notifications.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUtilisateurIdOrderByCreatedAtDesc(UUID utilisateurId, Pageable pageable);

    Page<Notification> findByUtilisateurIdAndLuFalseOrderByCreatedAtDesc(UUID utilisateurId, Pageable pageable);

    long countByUtilisateurIdAndLuFalse(UUID utilisateurId);

    @Modifying
    @Query("update Notification n set n.lu = true where n.id = :id and n.utilisateurId = :userId")
    int markRead(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query("update Notification n set n.lu = true where n.utilisateurId = :userId and n.lu = false")
    int markAllRead(@Param("userId") UUID userId);
}

