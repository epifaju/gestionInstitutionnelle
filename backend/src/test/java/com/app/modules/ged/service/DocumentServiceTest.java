package com.app.modules.ged.service;

import com.app.config.GedProperties;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.ged.dto.DocumentUploadRequest;
import com.app.modules.ged.repository.DocumentAccesRepository;
import com.app.modules.ged.repository.DocumentRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");

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

    @Test
    void upload_refuseSiTropGrand() throws Exception {
        when(gedProperties.getMaxFileBytes()).thenReturn(1L);

        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {1, 2});
        DocumentUploadRequest req = new DocumentUploadRequest(
                "Titre",
                null,
                "FACTURE",
                null, // tags
                null, // visibilite
                null, // service
                null, // entite type
                null, // entite id
                null, // date expiration
                null  // parent id
        );

        assertThatThrownBy(() -> documentService.upload(req, file, orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_TROP_GRAND");

        verify(documentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}

