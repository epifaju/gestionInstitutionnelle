package com.app.modules.inventaire.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.inventaire.dto.BienRequest;
import com.app.modules.inventaire.dto.StatsInventaireResponse;
import com.app.modules.inventaire.entity.BienMateriel;
import com.app.modules.inventaire.entity.EtatBien;
import com.app.modules.inventaire.entity.MouvementBien;
import com.app.modules.inventaire.entity.TypeMouvementBien;
import com.app.modules.inventaire.repository.BienMaterielRepository;
import com.app.modules.inventaire.repository.MouvementBienRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventaireServiceTest {

    @Mock private BienMaterielRepository bienRepo;
    @Mock private MouvementBienRepository mouvementRepo;
    @Mock private BienSequenceService bienSequenceService;
    @Mock private SalarieRepository salarieRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private InventaireService inventaireService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private final UUID bienId = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Test
    void reformer_motifVide_refuse() {
        assertThatThrownBy(() -> inventaireService.reformer(bienId, "  ", orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "MOTIF_REFORME_REQUIS");
        verify(bienRepo, never()).save(any());
    }

    @Test
    void creer_genereCode_etCreeMouvementCreation() {
        when(bienSequenceService.nextSequence(orgId, "MAT", 2026)).thenReturn(7);
        when(utilisateurRepository.getReferenceById(userId)).thenReturn(new Utilisateur());
        when(bienRepo.save(any(BienMateriel.class))).thenAnswer(inv -> {
            BienMateriel b = inv.getArgument(0);
            b.setId(bienId);
            return b;
        });
        when(bienRepo.findByIdAndOrganisationId(bienId, orgId)).thenReturn(Optional.empty());

        BienRequest req = new BienRequest(
                "PC",
                "Informatique",
                "MAT",
                LocalDate.of(2026, 1, 1),
                new BigDecimal("1000.00"),
                "",
                "Bureau",
                "BON",
                null,
                null
        );

        inventaireService.creer(req, orgId, userId);

        ArgumentCaptor<BienMateriel> cap = ArgumentCaptor.forClass(BienMateriel.class);
        verify(bienRepo).save(cap.capture());
        assertThat(cap.getValue().getCodeInventaire()).isEqualTo("MAT-2026-0007");
        assertThat(cap.getValue().getDevise()).isEqualTo("EUR");
        assertThat(cap.getValue().getEtat()).isEqualTo(EtatBien.BON);

        ArgumentCaptor<MouvementBien> mcap = ArgumentCaptor.forClass(MouvementBien.class);
        verify(mouvementRepo).save(mcap.capture());
        assertThat(mcap.getValue().getTypeMouvement()).isEqualTo(TypeMouvementBien.CREATION);
    }

    @Test
    void modifier_changeLocalisation_enregistreMouvementDeplacement() {
        BienMateriel b = new BienMateriel();
        b.setId(bienId);
        b.setOrganisationId(orgId);
        b.setCodeInventaire("MAT-2026-0001");
        b.setLibelle("PC");
        b.setCategorie("Info");
        b.setCodeCategorie("MAT");
        b.setDateAcquisition(LocalDate.of(2026, 1, 1));
        b.setValeurAchat(new BigDecimal("1000"));
        b.setDevise("EUR");
        b.setLocalisation("A");
        b.setEtat(EtatBien.BON);
        when(bienRepo.findByIdAndOrganisationId(bienId, orgId)).thenReturn(Optional.of(b));
        when(utilisateurRepository.getReferenceById(userId)).thenReturn(new Utilisateur());
        when(bienRepo.save(any(BienMateriel.class))).thenAnswer(inv -> inv.getArgument(0));

        BienRequest req = new BienRequest(
                "PC",
                "Info",
                "MAT",
                LocalDate.of(2026, 1, 1),
                new BigDecimal("1000"),
                "EUR",
                "B",
                "BON",
                null,
                null
        );

        inventaireService.modifier(bienId, req, orgId, userId);

        ArgumentCaptor<MouvementBien> mcap = ArgumentCaptor.forClass(MouvementBien.class);
        verify(mouvementRepo).save(mcap.capture());
        assertThat(mcap.getValue().getTypeMouvement()).isEqualTo(TypeMouvementBien.DEPLACEMENT);
        assertThat(mcap.getValue().getChampModifie()).isEqualTo("localisation");
        assertThat(mcap.getValue().getAncienneValeur()).isEqualTo("A");
        assertThat(mcap.getValue().getNouvelleValeur()).isEqualTo("B");
    }

    @Test
    void getHistorique_siBienAbsent_refuse() {
        when(bienRepo.findByIdAndOrganisationId(bienId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventaireService.getHistorique(bienId, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "BIEN");
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void getStats_retourneValeurs() {
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), eq(orgId)))
                .thenReturn(new BigDecimal("123.45"));
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(orgId)))
                .thenReturn(List.of());

        StatsInventaireResponse res = inventaireService.getStats(orgId);
        assertThat(res.valeurTotaleParc()).isEqualByComparingTo(new BigDecimal("123.45"));
    }
}

