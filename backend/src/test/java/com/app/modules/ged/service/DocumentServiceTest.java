package com.app.modules.ged.service;

import com.app.config.GedProperties;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.ged.dto.DocumentResponse;
import com.app.modules.ged.dto.DocumentUpdateRequest;
import com.app.modules.ged.dto.DocumentUploadRequest;
import com.app.modules.ged.entity.Document;
import com.app.modules.ged.entity.DocumentAcces;
import com.app.modules.ged.entity.DocumentAccesId;
import com.app.modules.ged.entity.VisibiliteDoc;
import com.app.modules.ged.repository.DocumentAccesRepository;
import com.app.modules.ged.repository.DocumentRepository;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID DOC_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentAccesRepository documentAccesRepository;
    @Mock
    private MinioStorageService minioStorageService;
    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private SalarieRepository salarieRepository;
    @Mock
    private GedProperties gedProperties;

    @InjectMocks
    private DocumentService documentService;

    @BeforeEach
    void stubGedDefaults() {
        lenient().when(gedProperties.getMaxFileBytes()).thenReturn(10_000L);
        lenient().when(gedProperties.getAllowedMimeTypes()).thenReturn(List.of("application/pdf"));
        lenient().when(gedProperties.getPresignExpirySeconds()).thenReturn(3600);
    }

    private static byte[] pdfBytes() {
        return "%PDF-1.4\n".getBytes();
    }

    private static MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfBytes());
    }

    private static DocumentUploadRequest uploadReq(UUID parentId) {
        return new DocumentUploadRequest(
                " Titre doc ",
                " desc ",
                "FACTURE",
                new String[] {"a", " a ", "b"},
                "ORGANISATION",
                null,
                null,
                null,
                LocalDate.of(2026, 12, 31),
                parentId);
    }

    private Document ownedDoc(VisibiliteDoc visibilite) {
        Document d = new Document();
        d.setId(DOC_ID);
        d.setOrganisationId(ORG_ID);
        d.setTitre("Doc");
        d.setTypeDocument("FACTURE");
        d.setFichierUrl(ORG_ID + "/ged/FACTURE/2026/x-doc.pdf");
        d.setNomFichier("doc.pdf");
        d.setTailleOctets(10);
        d.setMimeType("application/pdf");
        d.setVersion(1);
        d.setVisibilite(visibilite);
        d.setUploadePar(USER_ID);
        d.setCreatedAt(Instant.parse("2026-01-15T10:00:00Z"));
        d.setSupprime(false);
        return d;
    }

    private void stubSaveAssignsId() {
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            if (d.getCreatedAt() == null) {
                d.setCreatedAt(Instant.parse("2026-01-15T10:00:00Z"));
            }
            return d;
        });
    }

    @Test
    void upload_refuseSiTropGrand() throws Exception {
        when(gedProperties.getMaxFileBytes()).thenReturn(1L);

        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {1, 2});
        DocumentUploadRequest req = uploadReq(null);

        assertThatThrownBy(() -> documentService.upload(req, file, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_TROP_GRAND");

        verify(documentRepository, never()).save(any());
    }

    @Test
    void upload_refuseSiFichierAbsent() {
        DocumentUploadRequest req = uploadReq(null);
        MockMultipartFile empty = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> documentService.upload(req, empty, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_ABSENT");
    }

    @Test
    void upload_refuseSiTypeMimeInvalide() {
        MockMultipartFile file =
                new MockMultipartFile("file", "a.txt", "text/plain", "hello world".getBytes());

        assertThatThrownBy(() -> documentService.upload(uploadReq(null), file, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_TYPE_INVALIDE");

        verify(documentRepository, never()).save(any());
    }

    @Test
    void upload_ok_retourneResponseAvecPresign() throws Exception {
        stubSaveAssignsId();
        when(minioStorageService.presignedGetUrl(any(), anyInt())).thenReturn("https://minio/presign");

        Utilisateur uploader = new Utilisateur();
        uploader.setId(USER_ID);
        uploader.setNom("Doe");
        uploader.setPrenom("John");
        when(utilisateurRepository.findById(USER_ID)).thenReturn(Optional.of(uploader));

        DocumentResponse resp = documentService.upload(uploadReq(null), pdfFile(), ORG_ID, USER_ID);

        assertThat(resp.titre()).isEqualTo("Titre doc");
        assertThat(resp.typeDocument()).isEqualTo("FACTURE");
        assertThat(resp.version()).isEqualTo(1);
        assertThat(resp.visibilite()).isEqualTo("ORGANISATION");
        assertThat(resp.uploadeParNomComplet()).isEqualTo("Doe John");
        assertThat(resp.presignedUrl()).isEqualTo("https://minio/presign");
        assertThat(resp.tags()).containsExactly("a", "b");

        verify(minioStorageService).upload(any(), any(), anyLong(), eq("application/pdf"));
        ArgumentCaptor<Document> cap = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(cap.capture());
        assertThat(cap.getValue().getFichierUrl()).contains("/ged/FACTURE/");
        assertThat(cap.getValue().getUploadePar()).isEqualTo(USER_ID);
    }

    @Test
    void upload_avecParent_incrementeVersion() throws Exception {
        Document parent = ownedDoc(VisibiliteDoc.ORGANISATION);
        parent.setVersion(2);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(parent));
        stubSaveAssignsId();
        when(minioStorageService.presignedGetUrl(any(), anyInt())).thenReturn("https://minio/u");

        DocumentResponse resp =
                documentService.upload(uploadReq(DOC_ID), pdfFile(), ORG_ID, USER_ID);

        assertThat(resp.version()).isEqualTo(3);
        assertThat(resp.documentParentId()).isEqualTo(DOC_ID);
    }

    @Test
    void upload_refuseSiTypeDocumentVide() {
        DocumentUploadRequest req = new DocumentUploadRequest(
                "Titre", null, "  ", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> documentService.upload(req, pdfFile(), ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TYPE_DOCUMENT_INVALIDE");
    }

    @Test
    void getById_ok_pourUploader() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(ownedDoc(VisibiliteDoc.PRIVE)));
        when(utilisateurRepository.findById(USER_ID)).thenReturn(Optional.empty());

        DocumentResponse resp = documentService.getById(DOC_ID, ORG_ID, USER_ID);

        assertThat(resp.id()).isEqualTo(DOC_ID);
        assertThat(resp.titre()).isEqualTo("Doc");
    }

    @Test
    void getById_refuseSiPriveSansAcces() {
        Document d = ownedDoc(VisibiliteDoc.PRIVE);
        d.setUploadePar(OTHER_USER);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(d));
        when(documentAccesRepository.findById_DocumentIdAndId_UtilisateurId(DOC_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getById(DOC_ID, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCUMENT_ACCES_REFUSE");
    }

    @Test
    void getById_okSiVisibiliteOrganisation() {
        Document d = ownedDoc(VisibiliteDoc.ORGANISATION);
        d.setUploadePar(OTHER_USER);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(d));
        when(utilisateurRepository.findById(OTHER_USER)).thenReturn(Optional.empty());

        DocumentResponse resp = documentService.getById(DOC_ID, ORG_ID, USER_ID);
        assertThat(resp.id()).isEqualTo(DOC_ID);
    }

    @Test
    void getById_okSiServiceCorrespond() {
        Document d = ownedDoc(VisibiliteDoc.SERVICE);
        d.setUploadePar(OTHER_USER);
        d.setServiceCible("RH");
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(d));

        Salarie salarie = new Salarie();
        salarie.setService("RH");
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(ORG_ID, USER_ID))
                .thenReturn(Optional.of(salarie));
        when(utilisateurRepository.findById(OTHER_USER)).thenReturn(Optional.empty());

        assertThat(documentService.getById(DOC_ID, ORG_ID, USER_ID).id()).isEqualTo(DOC_ID);
    }

    @Test
    void getById_refuseSiServiceDifferent() {
        Document d = ownedDoc(VisibiliteDoc.SERVICE);
        d.setUploadePar(OTHER_USER);
        d.setServiceCible("RH");
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(d));

        Salarie salarie = new Salarie();
        salarie.setService("IT");
        when(salarieRepository.findByOrganisationIdAndUtilisateur_Id(ORG_ID, USER_ID))
                .thenReturn(Optional.of(salarie));

        assertThatThrownBy(() -> documentService.getById(DOC_ID, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCUMENT_ACCES_REFUSE");
    }

    @Test
    void getById_okSiPartagePrive() {
        Document d = ownedDoc(VisibiliteDoc.PRIVE);
        d.setUploadePar(OTHER_USER);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(d));
        when(documentAccesRepository.findById_DocumentIdAndId_UtilisateurId(DOC_ID, USER_ID))
                .thenReturn(Optional.of(new DocumentAcces()));
        when(utilisateurRepository.findById(OTHER_USER)).thenReturn(Optional.empty());

        assertThat(documentService.getById(DOC_ID, ORG_ID, USER_ID).id()).isEqualTo(DOC_ID);
    }

    @Test
    void getById_notFoundSiAutreOrg() {
        Document d = ownedDoc(VisibiliteDoc.ORGANISATION);
        d.setOrganisationId(UUID.fromString("a0000000-0000-0000-0000-000000000099"));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> documentService.getById(DOC_ID, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCUMENT_ABSENT");
    }

    @Test
    void getById_notFoundSiSupprime() {
        Document d = ownedDoc(VisibiliteDoc.ORGANISATION);
        d.setSupprime(true);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> documentService.getById(DOC_ID, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCUMENT_ABSENT");
    }

    @Test
    void generatePresignedUrl_ok() throws Exception {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(ownedDoc(VisibiliteDoc.ORGANISATION)));
        when(minioStorageService.presignedGetUrl(any(), eq(3600))).thenReturn("https://signed");

        assertThat(documentService.generatePresignedUrl(DOC_ID, ORG_ID, USER_ID)).isEqualTo("https://signed");
    }

    @Test
    void getObjectName_ok() {
        Document d = ownedDoc(VisibiliteDoc.ORGANISATION);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(d));

        assertThat(documentService.getObjectName(DOC_ID, ORG_ID, USER_ID)).isEqualTo(d.getFichierUrl());
    }

    @Test
    void modifier_ok_parUploader() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(ownedDoc(VisibiliteDoc.ORGANISATION)));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(utilisateurRepository.findById(USER_ID)).thenReturn(Optional.empty());

        DocumentUpdateRequest req = new DocumentUpdateRequest(
                "Nouveau titre",
                "desc",
                new String[] {"x"},
                "PRIVE",
                null,
                null,
                null,
                LocalDate.of(2027, 1, 1));

        DocumentResponse resp = documentService.modifier(DOC_ID, req, ORG_ID, USER_ID);

        assertThat(resp.titre()).isEqualTo("Nouveau titre");
        assertThat(resp.visibilite()).isEqualTo("PRIVE");
    }

    @Test
    void modifier_refuseSiPasDroit() {
        Document d = ownedDoc(VisibiliteDoc.ORGANISATION);
        d.setUploadePar(OTHER_USER);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(d));
        when(documentAccesRepository.findById_DocumentIdAndId_UtilisateurId(DOC_ID, USER_ID))
                .thenReturn(Optional.empty());

        DocumentUpdateRequest req =
                new DocumentUpdateRequest("T", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> documentService.modifier(DOC_ID, req, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCUMENT_MODIF_REFUSE");
    }

    @Test
    void modifier_ok_avecAccesModifier() {
        Document d = ownedDoc(VisibiliteDoc.ORGANISATION);
        d.setUploadePar(OTHER_USER);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(d));

        DocumentAcces acc = new DocumentAcces();
        acc.setPeutModifier(true);
        when(documentAccesRepository.findById_DocumentIdAndId_UtilisateurId(DOC_ID, USER_ID))
                .thenReturn(Optional.of(acc));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(utilisateurRepository.findById(OTHER_USER)).thenReturn(Optional.empty());

        DocumentUpdateRequest req =
                new DocumentUpdateRequest("Modifie", null, null, null, null, null, null, null);

        assertThat(documentService.modifier(DOC_ID, req, ORG_ID, USER_ID).titre()).isEqualTo("Modifie");
    }

    @Test
    void supprimer_ok_softDelete() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(ownedDoc(VisibiliteDoc.ORGANISATION)));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        documentService.supprimer(DOC_ID, ORG_ID, USER_ID);

        ArgumentCaptor<Document> cap = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(cap.capture());
        assertThat(cap.getValue().isSupprime()).isTrue();
        assertThat(cap.getValue().getDateSuppression()).isNotNull();
    }

    @Test
    void supprimer_refuseSiPasDroit() {
        Document d = ownedDoc(VisibiliteDoc.ORGANISATION);
        d.setUploadePar(OTHER_USER);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(d));
        when(documentAccesRepository.findById_DocumentIdAndId_UtilisateurId(DOC_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.supprimer(DOC_ID, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCUMENT_SUPPR_REFUSE");
    }

    @Test
    void getVersions_ok_trieParVersionDesc() {
        Document root = ownedDoc(VisibiliteDoc.ORGANISATION);
        root.setVersion(1);

        Document child = ownedDoc(VisibiliteDoc.ORGANISATION);
        child.setId(UUID.fromString("c0000000-0000-0000-0000-000000000002"));
        child.setVersion(2);
        child.setDocumentParent(root);

        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(root));
        when(documentRepository.findAllByOrganisationIdAndSupprimeFalseAndDocumentParent_IdOrderByVersionDesc(
                        ORG_ID, DOC_ID))
                .thenReturn(List.of(child));
        when(utilisateurRepository.findById(USER_ID)).thenReturn(Optional.empty());

        List<DocumentResponse> versions = documentService.getVersions(DOC_ID, ORG_ID, USER_ID);

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).version()).isEqualTo(2);
        assertThat(versions.get(1).version()).isEqualTo(1);
    }

    @Test
    void getDocumentsExpirantBientot_ok() {
        Document d = ownedDoc(VisibiliteDoc.ORGANISATION);
        d.setDateExpiration(LocalDate.now().plusDays(5));
        when(documentRepository.findExpiringBefore(eq(ORG_ID), any(LocalDate.class)))
                .thenReturn(List.of(d));
        when(utilisateurRepository.findById(USER_ID)).thenReturn(Optional.empty());

        List<DocumentResponse> out = documentService.getDocumentsExpirantBientot(ORG_ID, 0);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).id()).isEqualTo(DOC_ID);
    }

    @Test
    void partagerAvec_creeAcces() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(ownedDoc(VisibiliteDoc.PRIVE)));
        when(documentAccesRepository.findById(any(DocumentAccesId.class))).thenReturn(Optional.empty());
        when(documentAccesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        documentService.partagerAvec(DOC_ID, OTHER_USER, true, false, ORG_ID);

        ArgumentCaptor<DocumentAcces> cap = ArgumentCaptor.forClass(DocumentAcces.class);
        verify(documentAccesRepository).save(cap.capture());
        assertThat(cap.getValue().getId().getUtilisateurId()).isEqualTo(OTHER_USER);
        assertThat(cap.getValue().isPeutModifier()).isTrue();
        assertThat(cap.getValue().isPeutSupprimer()).isFalse();
    }

    @Test
    void search_delegueAuRepository() {
        Document d = ownedDoc(VisibiliteDoc.ORGANISATION);
        when(documentRepository.search(
                        eq(ORG_ID),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any(),
                        anyBoolean(),
                        anyInt(),
                        any()))
                .thenReturn(new PageImpl<>(List.of(d), PageRequest.of(0, 20), 1));

        var page = documentService.search(ORG_ID, "facture doc", "FACTURE", new String[] {"a"}, null, false, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).titre()).isEqualTo("Doc");
    }
}
