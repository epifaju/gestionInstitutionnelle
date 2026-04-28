package com.app.modules.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_rules")
@Getter
@Setter
public class WorkflowRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private WorkflowDefinition definition;

    @Column(name = "min_amount_eur", precision = 14, scale = 2)
    private BigDecimal minAmountEur;

    @Column(name = "max_amount_eur", precision = 14, scale = 2)
    private BigDecimal maxAmountEur;

    @Column(nullable = false)
    private int levels = 1;

    @Column(name = "level1_role", nullable = false, length = 40)
    private String level1Role;

    @Column(name = "level2_role", length = 40)
    private String level2Role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

