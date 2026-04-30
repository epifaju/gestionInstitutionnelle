package com.app.modules.rh.service;

import com.app.modules.rh.dto.TitreSejourRequest;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.ConfigAlerteRhRepository;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.modules.rh.repository.TitreSejourRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TitreSejourServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID salarieId = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Mock
    private TitreSejourRepository titreRepository;
    @Mock
    private SalarieRepository salarieRepository;
    @Mock
    private EcheanceRhRepository echeanceRepository;
    @Mock
    private ConfigAlerteRhRepository configRepository;
    @Mock
    private MinioStorageService minioStorageService;

    @InjectMocks
    private TitreSejourService titreSejourService;

    @Test
    void enregistrerTitre_salarieInconnu_lanceException() {
        when(salarieRepository.findByIdAndOrganisationId(salarieId, orgId)).thenReturn(Optional.empty());

        TitreSejourRequest req = new TitreSejourRequest(
                "Titre de séjour",
                "123",
                "France",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "Préfecture");

        assertThatThrownBy(() -> titreSejourService.enregistrerTitre(salarieId, req, null, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALARIE_NON_TROUVE");

        verify(titreRepository, never()).save(any());
    }

    @Test
    void enregistrerTitre_documentTropGrand_lanceException() {
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setNom("A");
        s.setPrenom("B");
        when(salarieRepository.findByIdAndOrganisationId(salarieId, orgId)).thenReturn(Optional.of(s));

        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(50_000_000L);

        TitreSejourRequest req = new TitreSejourRequest(
                "Titre de séjour",
                null,
                null,
                null,
                LocalDate.of(2026, 12, 31),
                null);

        assertThatThrownBy(() -> titreSejourService.enregistrerTitre(salarieId, req, file, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_TROP_GRAND");
    }
}

