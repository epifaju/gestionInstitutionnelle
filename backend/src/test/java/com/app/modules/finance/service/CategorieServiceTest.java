package com.app.modules.finance.service;

import com.app.modules.finance.dto.CategorieRequest;
import com.app.modules.finance.entity.CategorieDepense;
import com.app.modules.finance.entity.TypeCategorie;
import com.app.modules.finance.repository.CategorieDepenseRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorieServiceTest {

    @Mock private CategorieDepenseRepository categorieDepenseRepository;
    @InjectMocks private CategorieService categorieService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID catId = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Test
    void list_includeInactiveFalse_utiliseActives() {
        CategorieDepense c = new CategorieDepense();
        c.setId(catId);
        c.setLibelle("A");
        c.setCode("A1");
        c.setType(TypeCategorie.DEPENSE);
        c.setCouleur("#000000");
        c.setActif(true);

        when(categorieDepenseRepository.findByOrganisationIdAndActifTrueOrderByLibelleAsc(orgId)).thenReturn(List.of(c));

        var res = categorieService.list(orgId, false);

        assertThat(res).hasSize(1);
        verify(categorieDepenseRepository).findByOrganisationIdAndActifTrueOrderByLibelleAsc(orgId);
        verify(categorieDepenseRepository, never()).findByOrganisationIdOrderByLibelleAsc(orgId);
    }

    @Test
    void list_includeInactiveTrue_utiliseToutes() {
        when(categorieDepenseRepository.findByOrganisationIdOrderByLibelleAsc(orgId)).thenReturn(List.of());

        categorieService.list(orgId, true);

        verify(categorieDepenseRepository).findByOrganisationIdOrderByLibelleAsc(orgId);
    }

    @Test
    void creer_codeDuplique_refuse() {
        when(categorieDepenseRepository.existsByOrganisationIdAndCode(orgId, "CODE")).thenReturn(true);
        CategorieRequest req = new CategorieRequest("Lib", "CODE", "DEPENSE", "#111111");

        assertThatThrownBy(() -> categorieService.creer(req, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CATEGORIE_CODE_DUPLIQUE");
    }

    @Test
    void creer_couleurVide_metCouleurParDefaut() {
        when(categorieDepenseRepository.existsByOrganisationIdAndCode(orgId, "C1")).thenReturn(false);
        when(categorieDepenseRepository.save(any(CategorieDepense.class))).thenAnswer(inv -> {
            CategorieDepense c = inv.getArgument(0);
            c.setId(catId);
            return c;
        });

        var res = categorieService.creer(new CategorieRequest(" Lib ", "C1", "DEPENSE", "  "), orgId);

        assertThat(res.couleur()).isEqualTo("#6B7280");
        verify(categorieDepenseRepository).save(any(CategorieDepense.class));
    }

    @Test
    void modifier_codeChange_maisDejaPris_refuse() {
        CategorieDepense existing = new CategorieDepense();
        existing.setId(catId);
        existing.setOrganisationId(orgId);
        existing.setCode("OLD");
        existing.setLibelle("Old");
        existing.setType(TypeCategorie.DEPENSE);
        existing.setCouleur("#000");
        existing.setActif(true);

        when(categorieDepenseRepository.findByIdAndOrganisationId(catId, orgId)).thenReturn(Optional.of(existing));
        when(categorieDepenseRepository.existsByOrganisationIdAndCode(orgId, "NEW")).thenReturn(true);

        CategorieRequest req = new CategorieRequest("New", "NEW", "DEPENSE", "#111");

        assertThatThrownBy(() -> categorieService.modifier(catId, req, orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CATEGORIE_CODE_DUPLIQUE");
    }

    @Test
    void supprimer_softDelete_actifFalse() {
        CategorieDepense c = new CategorieDepense();
        c.setId(catId);
        c.setOrganisationId(orgId);
        c.setActif(true);
        when(categorieDepenseRepository.findByIdAndOrganisationId(catId, orgId)).thenReturn(Optional.of(c));
        when(categorieDepenseRepository.save(any(CategorieDepense.class))).thenAnswer(inv -> inv.getArgument(0));

        categorieService.supprimer(catId, orgId);

        assertThat(c.isActif()).isFalse();
        verify(categorieDepenseRepository).save(eq(c));
    }

    @Test
    void reactiver_actifTrue() {
        CategorieDepense c = new CategorieDepense();
        c.setId(catId);
        c.setOrganisationId(orgId);
        c.setActif(false);
        when(categorieDepenseRepository.findByIdAndOrganisationId(catId, orgId)).thenReturn(Optional.of(c));
        when(categorieDepenseRepository.save(any(CategorieDepense.class))).thenAnswer(inv -> inv.getArgument(0));

        categorieService.reactiver(catId, orgId);

        assertThat(c.isActif()).isTrue();
        verify(categorieDepenseRepository).save(eq(c));
    }
}

