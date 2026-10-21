package com.app.modules.courriers.service;

import com.app.modules.courriers.dto.CourrierRequest;
import com.app.modules.courriers.dto.CourrierResponse;
import com.app.modules.courriers.entity.CanalCourrier;
import com.app.modules.courriers.entity.Courrier;
import com.app.modules.courriers.entity.SensCourrier;
import com.app.modules.courriers.entity.StatutCourrier;
import com.app.modules.courriers.repository.CourrierRepository;
import com.app.modules.ged.entity.Document;
import com.app.modules.ged.repository.DocumentRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
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
class CourrierServiceTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID COURRIER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID DOC_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");

    @Mock private CourrierRepository courrierRepository;
    @Mock private DocumentRepository documentRepository;

    @InjectMocks private CourrierService courrierService;

    private CourrierRequest req(String sens) {
        return new CourrierRequest(
                sens,
                null,
                "REF-1",
                "Objet test",
                "Expéditeur",
                "Destinataire",
                LocalDate.of(2026, 1, 15),
                null,
                null,
                "EMAIL",
                "Notes",
                null);
    }

    private Courrier entity() {
        Courrier c = new Courrier();
        c.setId(COURRIER_ID);
        c.setOrganisationId(ORG_ID);
        c.setSens(SensCourrier.ENTREE);
        c.setStatut(StatutCourrier.BROUILLON);
        c.setObjet("Objet test");
        c.setDateCourrier(LocalDate.of(2026, 1, 15));
        c.setCanal(CanalCourrier.EMAIL);
        c.setCreatedBy(USER_ID);
        return c;
    }

    @Test
    void create_ok() {
        when(courrierRepository.existsByOrganisationIdAndReferenceIgnoreCase(ORG_ID, "REF-1")).thenReturn(false);
        when(courrierRepository.save(any(Courrier.class))).thenAnswer(inv -> {
            Courrier c = inv.getArgument(0);
            c.setId(COURRIER_ID);
            return c;
        });

        CourrierResponse res = courrierService.create(req("ENTREE"), ORG_ID, USER_ID);

        assertThat(res.id()).isEqualTo(COURRIER_ID);
        assertThat(res.sens()).isEqualTo("ENTREE");
        assertThat(res.statut()).isEqualTo("BROUILLON");
        assertThat(res.canal()).isEqualTo("EMAIL");
        ArgumentCaptor<Courrier> cap = ArgumentCaptor.forClass(Courrier.class);
        verify(courrierRepository).save(cap.capture());
        assertThat(cap.getValue().getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(cap.getValue().getCreatedBy()).isEqualTo(USER_ID);
    }

    @Test
    void create_refuseSensInvalide() {
        assertThatThrownBy(() -> courrierService.create(req("BIDON"), ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COURRIER_SENS_INVALIDE");
        verify(courrierRepository, never()).save(any());
    }

    @Test
    void create_refuseReferenceDupliquee() {
        when(courrierRepository.existsByOrganisationIdAndReferenceIgnoreCase(ORG_ID, "REF-1")).thenReturn(true);
        assertThatThrownBy(() -> courrierService.create(req("SORTIE"), ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COURRIER_REFERENCE_DUPLIQUEE");
    }

    @Test
    void list_filtreInvalideIgnore() {
        when(courrierRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(entity())));

        var page = courrierService.list(ORG_ID, "NOT_A_SENS", "NOT_A_STATUT", null, null, null, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).objet()).isEqualTo("Objet test");
    }

    @Test
    void update_refuseSiClasseSansAdmin() {
        Courrier c = entity();
        c.setStatut(StatutCourrier.CLASSE);
        when(courrierRepository.findByIdAndOrganisationId(COURRIER_ID, ORG_ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> courrierService.update(COURRIER_ID, req("ENTREE"), ORG_ID, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COURRIER_VERROUILLE");
    }

    @Test
    void changerStatut_ok() {
        when(courrierRepository.findByIdAndOrganisationId(COURRIER_ID, ORG_ID)).thenReturn(Optional.of(entity()));
        when(courrierRepository.save(any(Courrier.class))).thenAnswer(inv -> inv.getArgument(0));

        CourrierResponse res = courrierService.changerStatut(COURRIER_ID, "ENREGISTRE", ORG_ID, false);
        assertThat(res.statut()).isEqualTo("ENREGISTRE");
    }

    @Test
    void rattacherDocument_ok() {
        when(courrierRepository.findByIdAndOrganisationId(COURRIER_ID, ORG_ID)).thenReturn(Optional.of(entity()));
        Document doc = new Document();
        doc.setId(DOC_ID);
        doc.setOrganisationId(ORG_ID);
        doc.setSupprime(false);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(courrierRepository.save(any(Courrier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        CourrierResponse res = courrierService.rattacherDocument(COURRIER_ID, DOC_ID, ORG_ID, false);

        assertThat(res.documentId()).isEqualTo(DOC_ID);
        assertThat(doc.getEntiteLieeType()).isEqualTo("COURRIER_REGISTRE");
        assertThat(doc.getEntiteLieeId()).isEqualTo(COURRIER_ID);
    }

    @Test
    void rattacherDocument_refuseDocAutreOrg() {
        when(courrierRepository.findByIdAndOrganisationId(COURRIER_ID, ORG_ID)).thenReturn(Optional.of(entity()));
        Document doc = new Document();
        doc.setId(DOC_ID);
        doc.setOrganisationId(UUID.randomUUID());
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> courrierService.rattacherDocument(COURRIER_ID, DOC_ID, ORG_ID, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCUMENT_NON_TROUVE");
    }

    @Test
    void getById_notFound() {
        when(courrierRepository.findByIdAndOrganisationId(COURRIER_ID, ORG_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> courrierService.getById(COURRIER_ID, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COURRIER_NON_TROUVE");
    }

    @Test
    void update_adminPeutModifierClasse() {
        Courrier c = entity();
        c.setStatut(StatutCourrier.CLASSE);
        when(courrierRepository.findByIdAndOrganisationId(COURRIER_ID, ORG_ID)).thenReturn(Optional.of(c));
        when(courrierRepository.existsByOrganisationIdAndReferenceIgnoreCaseAndIdNot(ORG_ID, "REF-1", COURRIER_ID))
                .thenReturn(false);
        when(courrierRepository.save(any(Courrier.class))).thenAnswer(inv -> inv.getArgument(0));

        CourrierResponse res = courrierService.update(COURRIER_ID, req("SORTIE"), ORG_ID, true);
        assertThat(res.sens()).isEqualTo("SORTIE");
    }

    @Test
    void create_canalParDefautSiBlank() {
        CourrierRequest blankCanal = new CourrierRequest(
                "ENTREE", null, null, "Objet", null, null, LocalDate.of(2026, 3, 1),
                null, null, "  ", null, null);
        when(courrierRepository.save(any(Courrier.class))).thenAnswer(inv -> {
            Courrier c = inv.getArgument(0);
            c.setId(COURRIER_ID);
            return c;
        });

        CourrierResponse res = courrierService.create(blankCanal, ORG_ID, USER_ID);
        assertThat(res.canal()).isEqualTo("COURRIER_POSTAL");
    }

    @Test
    void create_refuseCanalInvalide() {
        CourrierRequest bad = new CourrierRequest(
                "ENTREE", null, null, "Objet", null, null, LocalDate.of(2026, 3, 1),
                null, null, "FAX", null, null);
        assertThatThrownBy(() -> courrierService.create(bad, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COURRIER_CANAL_INVALIDE");
    }

    @Test
    void changerStatut_refuseStatutInvalide() {
        when(courrierRepository.findByIdAndOrganisationId(COURRIER_ID, ORG_ID)).thenReturn(Optional.of(entity()));
        assertThatThrownBy(() -> courrierService.changerStatut(COURRIER_ID, "XYZ", ORG_ID, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COURRIER_STATUT_INVALIDE");
    }
}
