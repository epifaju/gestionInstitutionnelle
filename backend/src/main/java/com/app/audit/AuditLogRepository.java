package com.app.audit;

import com.app.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByOrganisationIdOrderByDateActionDesc(UUID organisationId, Pageable pageable);

    @Query(
            """
            SELECT a
            FROM AuditLog a
            WHERE a.organisationId = :orgId
              AND a.dateAction >= :start
              AND a.dateAction < :end
              AND (:entite IS NULL OR a.entite = :entite)
              AND (:action IS NULL OR a.action = :action)
              AND (:userId IS NULL OR a.utilisateurId = :userId)
            ORDER BY a.dateAction DESC
            """)
    Page<AuditLog> findForExport(
            @Param("orgId") UUID orgId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("entite") String entite,
            @Param("action") String action,
            @Param("userId") UUID userId,
            Pageable pageable);

    @Query(
            """
            SELECT COUNT(a)
            FROM AuditLog a
            WHERE a.organisationId = :orgId
              AND a.dateAction >= :start
              AND a.dateAction < :end
              AND (:entite IS NULL OR a.entite = :entite)
              AND (:action IS NULL OR a.action = :action)
              AND (:userId IS NULL OR a.utilisateurId = :userId)
            """)
    long countForExport(
            @Param("orgId") UUID orgId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("entite") String entite,
            @Param("action") String action,
            @Param("userId") UUID userId);
}
