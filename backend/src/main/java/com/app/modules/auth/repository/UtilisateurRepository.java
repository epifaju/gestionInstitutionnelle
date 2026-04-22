package com.app.modules.auth.repository;

import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {

    List<Utilisateur> findAllByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "organisation")
    Optional<Utilisateur> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);

    @EntityGraph(attributePaths = "organisation")
    Optional<Utilisateur> findById(UUID id);

    @EntityGraph(attributePaths = "organisation")
    Optional<Utilisateur> findByIdAndOrganisationId(UUID id, UUID organisationId);

    Page<Utilisateur> findByOrganisationIdOrderByEmailAsc(UUID organisationId, Pageable pageable);

    boolean existsByOrganisationIdAndEmailIgnoreCase(UUID organisationId, String email);

    long countByOrganisationIdAndRoleAndActifTrue(UUID organisationId, Role role);
}
