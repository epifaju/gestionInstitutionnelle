package com.app.modules.ged.repository;

import com.app.modules.ged.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query(value = """
            select d.*
            from documents d
            where d.organisation_id = :orgId
              and d.supprime = false
              and (:type is null or d.type_document = :type)
              and (:service is null or d.service_cible = :service)
              and (:expSoon = false or (d.date_expiration is not null and d.date_expiration < (current_date + :nbJours)))
              and (:tagsEmpty = true or d.tags @> cast(:tags as text[]))
              and (
                :q is null
                or to_tsvector('french', d.titre || ' ' || coalesce(d.description,'')) @@ to_tsquery('french', :q)
              )
            order by d.created_at desc
            """,
            countQuery = """
            select count(*)
            from documents d
            where d.organisation_id = :orgId
              and d.supprime = false
              and (:type is null or d.type_document = :type)
              and (:service is null or d.service_cible = :service)
              and (:expSoon = false or (d.date_expiration is not null and d.date_expiration < (current_date + :nbJours)))
              and (:tagsEmpty = true or d.tags @> cast(:tags as text[]))
              and (
                :q is null
                or to_tsvector('french', d.titre || ' ' || coalesce(d.description,'')) @@ to_tsquery('french', :q)
              )
            """,
            nativeQuery = true)
    Page<Document> search(
            @Param("orgId") UUID orgId,
            @Param("q") String tsQuery,
            @Param("type") String type,
            @Param("tags") String[] tags,
            @Param("tagsEmpty") boolean tagsEmpty,
            @Param("service") String service,
            @Param("expSoon") boolean expSoon,
            @Param("nbJours") int nbJours,
            Pageable pageable);

    List<Document> findAllByOrganisationIdAndSupprimeFalseAndDocumentParent_IdOrderByVersionDesc(UUID orgId, UUID parentId);

    @Query("select d from Document d where d.organisationId = :orgId and d.supprime = false and d.dateExpiration is not null and d.dateExpiration < :limit order by d.dateExpiration asc")
    List<Document> findExpiringBefore(@Param("orgId") UUID orgId, @Param("limit") LocalDate limit);
}

