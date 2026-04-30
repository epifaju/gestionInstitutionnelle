package com.app.modules.rh.service;

import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.rh.dto.EcheanceRequest;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EcheanceServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID salarieId = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Mock
    private EcheanceRhRepository echeanceRepository;
    @Mock
    private SalarieRepository salarieRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private MinioStorageService minioStorageService;

    @InjectMocks
    private EcheanceService echeanceService;

    @Test
    void creerEcheance_refuseSiDateNonFuture() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        when(salarieRepository.findByIdAndOrganisationId(salarieId, orgId)).thenReturn(Optional.of(s));

        EcheanceRequest req = new EcheanceRequest(
                salarieId,
                null,
                "FIN_CDD",
                "Test",
                null,
                LocalDate.now(), // invalid: not after today
                2,
                null
        );

        assertThatThrownBy(() -> echeanceService.creerEcheance(req, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ECHEANCE_DATE_INVALIDE");

        verify(echeanceRepository, never()).save(any());
    }

    @Test
    void creerEcheance_refuseSiDoublonActif() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        when(salarieRepository.findByIdAndOrganisationId(salarieId, orgId)).thenReturn(Optional.of(s));
        when(echeanceRepository.existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(eq(orgId), eq(salarieId), any(), any(), any()))
                .thenReturn(true);

        EcheanceRequest req = new EcheanceRequest(
                salarieId,
                null,
                "FIN_CDD",
                "Test",
                null,
                LocalDate.now().plusDays(10),
                2,
                null
        );

        assertThatThrownBy(() -> echeanceService.creerEcheance(req, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ECHEANCE_DEJA_EXISTANTE");
    }
}

