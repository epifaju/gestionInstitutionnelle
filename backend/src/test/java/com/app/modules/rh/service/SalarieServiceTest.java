package com.app.modules.rh.service;

import com.app.audit.AuditLogService;
import com.app.modules.rh.repository.DroitsCongesRepository;
import com.app.modules.rh.repository.HistoriqueSalaireRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalarieServiceTest {

    @Mock private SalarieRepository salarieRepository;
    @Mock private HistoriqueSalaireRepository historiqueSalaireRepository;
    @Mock private DroitsCongesRepository droitsCongesRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private MinioStorageService minioStorageService;

    @InjectMocks private SalarieService salarieService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Test
    void getMe_SansLienEtSansEmail_TrouvePas() {
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salarieService.getMe(orgId, userId, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALARIE_ABSENT");
    }

    @Test
    void getMe_SansLien_EmailFallback_TrouvePas() {
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(orgId, userId)).thenReturn(Optional.empty());
        when(salarieRepository.findByOrganisationIdAndEmailIgnoreCase(orgId, "emp@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salarieService.getMe(orgId, userId, "emp@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALARIE_ABSENT");
    }
}

