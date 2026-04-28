package com.app.modules.templates.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "template_revisions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "version"})
)
@Getter
@Setter
public class TemplateRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private TemplateDefinition template;

    @Column(nullable = false)
    private int version;

    @Column(name = "content_document_id")
    private UUID contentDocumentId;

    @Column(name = "content_object_name", columnDefinition = "text")
    private String contentObjectName;

    @Column(name = "content_mime", nullable = false, length = 120)
    private String contentMime;

    @Column(length = 80)
    private String checksum;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

