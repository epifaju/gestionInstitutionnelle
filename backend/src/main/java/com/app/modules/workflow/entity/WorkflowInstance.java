package com.app.modules.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "workflow_instances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organisation_id", "process_key", "subject_type", "subject_id"})
)
@Getter
@Setter
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "process_key", nullable = false, length = 80)
    private String processKey;

    @Column(name = "subject_type", nullable = false, length = 80)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "amount_eur", precision = 14, scale = 2)
    private BigDecimal amountEur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkflowInstanceStatus status = WorkflowInstanceStatus.PENDING;

    @Column(name = "current_level", nullable = false)
    private int currentLevel = 1;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

