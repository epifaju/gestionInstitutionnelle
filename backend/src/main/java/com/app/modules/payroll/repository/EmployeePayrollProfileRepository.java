package com.app.modules.payroll.repository;

import com.app.modules.payroll.entity.EmployeePayrollProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeePayrollProfileRepository extends JpaRepository<EmployeePayrollProfile, UUID> {
    Optional<EmployeePayrollProfile> findByOrganisationIdAndSalarie_Id(UUID organisationId, UUID salarieId);
}

