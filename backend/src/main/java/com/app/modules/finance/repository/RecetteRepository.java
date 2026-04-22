package com.app.modules.finance.repository;

import com.app.modules.finance.entity.Recette;
import com.app.modules.finance.entity.TypeRecette;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface RecetteRepository extends JpaRepository<Recette, UUID> {

    @Query(
            """
                    SELECT r FROM Recette r
                    WHERE r.organisationId = :orgId
                    AND COALESCE(:type, r.typeRecette) = r.typeRecette
                    AND r.dateRecette >= COALESCE(:debut, r.dateRecette)
                    AND r.dateRecette <= COALESCE(:fin, r.dateRecette)
                    ORDER BY r.dateRecette DESC, r.createdAt DESC
                    """)
    Page<Recette> search(
            @Param("orgId") UUID orgId,
            @Param("type") TypeRecette type,
            @Param("debut") LocalDate debut,
            @Param("fin") LocalDate fin,
            Pageable pageable);
}
