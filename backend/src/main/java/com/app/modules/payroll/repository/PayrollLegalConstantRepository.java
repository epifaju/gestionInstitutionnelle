package com.app.modules.payroll.repository;

import com.app.modules.payroll.entity.PayrollLegalConstant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PayrollLegalConstantRepository extends JpaRepository<PayrollLegalConstant, UUID> {

    @Query("""
            select c from PayrollLegalConstant c
            where c.organisationId = :orgId
              and c.code = :code
              and c.effectiveFrom <= :d
              and (c.effectiveTo is null or c.effectiveTo >= :d)
            order by c.effectiveFrom desc
            """)
    Optional<PayrollLegalConstant> findEffective(@Param("orgId") UUID orgId, @Param("code") String code, @Param("d") LocalDate date);
}

