package com.app.modules.payroll.repository;

import com.app.modules.payroll.entity.PayrollCotisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PayrollCotisationRepository extends JpaRepository<PayrollCotisation, UUID> {

    @Query("""
            select c from PayrollCotisation c
            where c.organisationId = :orgId
              and c.actif = true
              and c.effectiveFrom <= :d
              and (c.effectiveTo is null or c.effectiveTo >= :d)
            order by c.ordreAffichage asc, c.code asc
            """)
    List<PayrollCotisation> listEffective(@Param("orgId") UUID orgId, @Param("d") LocalDate date);
}

