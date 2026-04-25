package com.app.modules.payroll.repository;

import com.app.modules.payroll.entity.PayrollEmployerSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PayrollEmployerSettingsRepository extends JpaRepository<PayrollEmployerSettings, UUID> {}

