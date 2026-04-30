package com.app.modules.rapports.service;

import com.app.config.MinioProperties;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.shared.exception.BusinessException;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PdfBuilderServiceTest {

    @Test
    void creerTableau_entetesEtLargeursMismatch_lanceException() {
        ConfigExportRepository cfgRepo = mock(ConfigExportRepository.class);
        OrganisationRepository orgRepo = mock(OrganisationRepository.class);
        MinioClient minio = mock(MinioClient.class);
        MinioProperties props = new MinioProperties();
        props.setBucket("bucket");

        PdfBuilderService svc = new PdfBuilderService(cfgRepo, orgRepo, minio, props);

        assertThatThrownBy(() -> svc.creerTableau(new String[] {"A", "B"}, new float[] {10}))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXPORT_TABLE_INVALID");
    }
}

