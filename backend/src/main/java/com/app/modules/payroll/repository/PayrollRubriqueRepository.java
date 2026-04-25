package com.app.modules.payroll.repository;

import com.app.modules.payroll.entity.PayrollRubrique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PayrollRubriqueRepository extends JpaRepository<PayrollRubrique, UUID> {

    @Query("""
            select r from PayrollRubrique r
            where r.organisationId = :orgId
              and r.actif = true
              and r.effectiveFrom <= :d
              and (r.effectiveTo is null or r.effectiveTo >= :d)
            order by r.ordreAffichage asc, r.code asc
            """)
    List<PayrollRubrique> listEffective(@Param("orgId") UUID orgId, @Param("d") LocalDate date);
}

