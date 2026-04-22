package com.app.modules.auth.repository;

import com.app.modules.auth.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {

    List<Organisation> findByActifTrue();
}
