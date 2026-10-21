package com.app.modules.rh.service;

import com.app.modules.rh.dto.TitreSejourRequest;
import com.app.modules.rh.entity.EcheanceRh;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.TitreSejour;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TitreSejourServiceTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SALARIE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID TITRE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID ECHEANCE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");

    @Mock private TitreSejourRepository titreRepository;
    @Mock private SalarieRepository salarieRepository;
    @Mock private EcheanceRhRepository echeanceRepository;
    @Mock private ConfigAlerteRhRepository configRepository;
    @Mock private MinioStorageService minioStorageService;

    @InjectMocks private TitreSejourService titreSejourService;

    private Salarie salarie() {
        Salarie s = new Salarie();
        s.setId(SALARIE_ID);
        s.setOrganisationId(ORG_ID);
        s.setNom("Dupont");
        s.setPrenom("Alice");
        return s;
    }

    @Test
    void enregistrerTitre_salarieInconnu_lanceException() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.empty());
        TitreSejourRequest req = new TitreSejourRequest(
                "Titre de séjour", "123", "France",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "Préfecture");

        assertThatThrownBy(() -> titreSejourService.enregistrerTitre(SALARIE_ID, req, null, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALARIE_NON_TROUVE");
        verify(titreRepository, never()).save(any());
    }

    @Test
    void enregistrerTitre_documentTropGrand_lanceException() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(50_000_000L);
        TitreSejourRequest req = new TitreSejourRequest(
                "Titre de séjour", null, null, null, LocalDate.of(2026, 12, 31), null);

        assertThatThrownBy(() -> titreSejourService.enregistrerTitre(SALARIE_ID, req, file, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_TROP_GRAND");
    }

    @Test
    void enregistrerTitre_ok_creeEcheance() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        when(configRepository.findByOrganisationId(ORG_ID)).thenReturn(Optional.empty());
        when(titreRepository.save(any(TitreSejour.class))).thenAnswer(inv -> {
            TitreSejour t = inv.getArgument(0);
            if (t.getId() == null) t.setId(TITRE_ID);
            return t;
        });
        when(echeanceRepository.save(any(EcheanceRh.class))).thenAnswer(inv -> {
            EcheanceRh e = inv.getArgument(0);
            e.setId(ECHEANCE_ID);
            return e;
        });

        TitreSejourRequest req = new TitreSejourRequest(
                "Titre de séjour",
                "ABC",
                "FR",
                LocalDate.of(2025, 1, 1),
                LocalDate.now().plusDays(120),
                "Préfecture");

        var out = titreSejourService.enregistrerTitre(SALARIE_ID, req, null, ORG_ID);
        assertThat(out.id()).isEqualTo(TITRE_ID);
        assertThat(out.typeDocument()).isEqualTo("Titre de séjour");
        assertThat(out.niveauUrgence()).isEqualTo("NORMAL");
        verify(echeanceRepository).save(any(EcheanceRh.class));
    }

    @Test
    void mettreAJourStatutRenouvellement_ok() {
        TitreSejour t = new TitreSejour();
        t.setId(TITRE_ID);
        t.setOrganisationId(ORG_ID);
        t.setSalarie(salarie());
        t.setTypeDocument("Titre");
        t.setDateExpiration(LocalDate.now().plusDays(5));
        when(titreRepository.findById(TITRE_ID)).thenReturn(Optional.of(t));
        when(titreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var out = titreSejourService.mettreAJourStatutRenouvellement(TITRE_ID, "EN_COURS", ORG_ID);
        assertThat(out.statutRenouvellement()).isEqualTo("EN_COURS");
        assertThat(out.niveauUrgence()).isEqualTo("URGENT");
    }

    @Test
    void mettreAJourStatutRenouvellement_refuseAutreOrg() {
        TitreSejour t = new TitreSejour();
        t.setId(TITRE_ID);
        t.setOrganisationId(UUID.randomUUID());
        when(titreRepository.findById(TITRE_ID)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> titreSejourService.mettreAJourStatutRenouvellement(TITRE_ID, "X", ORG_ID))
                .isInstanceOf(BusinessException.class);
        verify(titreRepository, never()).save(any());
    }

    @Test
    void getTitresSalarie_ok() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        TitreSejour t = new TitreSejour();
        t.setId(TITRE_ID);
        t.setOrganisationId(ORG_ID);
        t.setSalarie(salarie());
        t.setTypeDocument("Passeport");
        t.setDateExpiration(LocalDate.now().minusDays(1));
        t.setDocumentUrl("https://cdn/x.pdf");
        when(titreRepository.findBySalarieIdOrderByDateExpirationAsc(SALARIE_ID)).thenReturn(List.of(t));

        var out = titreSejourService.getTitresSalarie(SALARIE_ID, ORG_ID);
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().niveauUrgence()).isEqualTo("CRITIQUE");
        assertThat(out.getFirst().documentUrl()).isEqualTo("https://cdn/x.pdf");
    }
}
