package com.app.modules.workflow.repository;

import com.app.modules.workflow.entity.WorkflowDelegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowDelegationRepository extends JpaRepository<WorkflowDelegation, UUID> {

    @Query("""
            select d from WorkflowDelegation d
            where d.organisationId = :orgId
              and d.fromRole = :fromRole
              and :now between d.startAt and d.endAt
            """)
    Optional<WorkflowDelegation> findActiveByRole(@Param("orgId") UUID orgId, @Param("fromRole") String fromRole, @Param("now") OffsetDateTime now);

    @Query("""
            select d from WorkflowDelegation d
            where d.organisationId = :orgId
              and d.toUserId = :toUserId
              and :now between d.startAt and d.endAt
            order by d.startAt desc
            """)
    List<WorkflowDelegation> findActiveForUser(@Param("orgId") UUID orgId, @Param("toUserId") UUID toUserId, @Param("now") OffsetDateTime now);
}

