package com.app.modules.rh.service;

import com.app.modules.rh.dto.VisiteMedicaleRequest;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.modules.rh.repository.VisiteMedicaleRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisiteMedicaleServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID salarieId = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Mock
    private VisiteMedicaleRepository visiteRepository;
    @Mock
    private SalarieRepository salarieRepository;
    @Mock
    private EcheanceRhRepository echeanceRepository;
    @Mock
    private MinioStorageService minioStorageService;

    @InjectMocks
    private VisiteMedicaleService visiteMedicaleService;

    @Test
    void creerVisite_refuseSiDateRealiseeAvantPlanifiee() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        when(salarieRepository.findByIdAndOrganisationId(salarieId, orgId)).thenReturn(Optional.of(s));

        VisiteMedicaleRequest req = new VisiteMedicaleRequest(
                "Embauche",
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 4, 1), // réalisée avant planifiée => invalide
                null,
                null,
                "OK",
                null,
                24
        );

        assertThatThrownBy(() -> visiteMedicaleService.creerVisite(salarieId, req, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ECHEANCE_DATE_INVALIDE");

        verify(visiteRepository, never()).save(any());
    }
}

