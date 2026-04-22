package com.app.modules.auth.service;

import com.app.audit.AuditLogRepository;
import com.app.audit.AuditLogService;
import com.app.modules.auth.dto.AdminUserCreateRequest;
import com.app.modules.auth.dto.AdminUserUpdateRequest;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AdminService adminService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID actorId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Test
    void createUser_emailDejaUtilise_refuse() {
        when(utilisateurRepository.existsByOrganisationIdAndEmailIgnoreCase(orgId, "x@test.com")).thenReturn(true);

        AdminUserCreateRequest req = new AdminUserCreateRequest("x@test.com", "Pass123!", "Nom", "Prenom", Role.RH);

        assertThatThrownBy(() -> adminService.createUser(req, orgId, actorId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_DEJA_UTILISE");
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void updateUser_selfDeactivate_refuse() {
        Utilisateur u = new Utilisateur();
        u.setId(actorId);
        u.setOrganisationId(orgId);
        u.setEmail("admin@test.com");
        u.setRole(Role.ADMIN);
        u.setActif(true);
        when(utilisateurRepository.findByIdAndOrganisationId(actorId, orgId)).thenReturn(Optional.of(u));

        AdminUserUpdateRequest req = new AdminUserUpdateRequest(null, null, null, false, null);

        assertThatThrownBy(() -> adminService.updateUser(actorId, req, orgId, actorId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_SELF_DEACTIVATE");
    }

    @Test
    void updateUser_interditRetirerDernierAdminActif() {
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setOrganisationId(orgId);
        u.setEmail("admin@test.com");
        u.setRole(Role.ADMIN);
        u.setActif(true);
        when(utilisateurRepository.findByIdAndOrganisationId(userId, orgId)).thenReturn(Optional.of(u));
        when(utilisateurRepository.countByOrganisationIdAndRoleAndActifTrue(orgId, Role.ADMIN)).thenReturn(1L);

        AdminUserUpdateRequest req = new AdminUserUpdateRequest(null, null, Role.RH, null, null);

        assertThatThrownBy(() -> adminService.updateUser(userId, req, orgId, actorId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "LAST_ADMIN_FORBIDDEN");
    }

    @Test
    void updateUser_aucunPatch_retourneSansSave() {
        Utilisateur u = new Utilisateur();
        u.setId(userId);
        u.setOrganisationId(orgId);
        u.setEmail("u@test.com");
        u.setRole(Role.RH);
        u.setActif(true);
        when(utilisateurRepository.findByIdAndOrganisationId(userId, orgId)).thenReturn(Optional.of(u));

        AdminUserUpdateRequest req = new AdminUserUpdateRequest(null, null, null, null, null);
        adminService.updateUser(userId, req, orgId, actorId);

        verify(utilisateurRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
}

