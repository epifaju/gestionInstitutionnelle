package com.app.modules.finance.repository;

import com.app.modules.finance.entity.TauxChangeHistorique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TauxChangeHistoriqueRepository extends JpaRepository<TauxChangeHistorique, UUID> {

    Optional<TauxChangeHistorique> findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc(
            String deviseBase, String deviseCible, LocalDate dateTaux);

    Optional<TauxChangeHistorique> findTopByDeviseBaseAndDeviseCibleAndDateTauxLessThanEqualOrderByDateTauxDescCreatedAtDesc(
            String deviseBase, String deviseCible, LocalDate dateTaux);

    List<TauxChangeHistorique> findByDeviseBaseAndDeviseCibleAndDateTauxBetweenOrderByDateTauxAsc(
            String deviseBase, String deviseCible, LocalDate debut, LocalDate fin);

    @Query(
            """
            select t
            from TauxChangeHistorique t
            where t.deviseBase = :base
              and t.dateTaux = :date
            order by t.deviseCible asc, t.createdAt desc
            """)
    List<TauxChangeHistorique> listAllForDate(String base, LocalDate date);
}

