package com.app.modules.rapports.service;

import com.app.config.MinioProperties;
import com.app.modules.rapports.dto.ExportJobResponse;
import com.app.modules.rapports.entity.ExportJob;
import com.app.modules.rapports.entity.StatutExportJob;
import com.app.modules.rapports.entity.TypeExport;
import com.app.modules.rapports.repository.ExportJobRepository;
import com.app.shared.exception.BusinessException;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
class ExportJobServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID userId = UUID.fromString("b1111111-1111-4111-8111-111111111101");

    @Mock
    private ExportJobRepository exportJobRepository;
    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioProperties minioProperties;

    @InjectMocks
    private ExportJobService exportJobService;

    @Test
    void creerJob_refuseSiJobDejaEnCours() {
        when(exportJobRepository.existsByOrganisationIdAndTypeExportAndStatutIn(eq(orgId), eq(TypeExport.NOTE_FRAIS_PDF), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> exportJobService.creerJob(orgId, userId, TypeExport.NOTE_FRAIS_PDF, Map.of("missionId", UUID.randomUUID())))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EXPORT_JOB_EN_COURS");

        verify(exportJobRepository, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void creerJob_persisteEtPublieEvent() {
        when(exportJobRepository.existsByOrganisationIdAndTypeExportAndStatutIn(eq(orgId), eq(TypeExport.ETAT_PAIE_PDF), any()))
                .thenReturn(false);

        UUID jobId = UUID.randomUUID();
        when(exportJobRepository.saveAndFlush(any(ExportJob.class)))
                .thenAnswer(inv -> {
                    ExportJob j = inv.getArgument(0);
                    j.setId(jobId);
                    return j;
                });

        ExportJobResponse r = exportJobService.creerJob(orgId, userId, TypeExport.ETAT_PAIE_PDF, Map.of("annee", 2026));
        assertThat(r.id()).isEqualTo(jobId);
        assertThat(r.statut()).isEqualTo(StatutExportJob.EN_ATTENTE.name());
        assertThat(r.progression()).isEqualTo(0);

        verify(publisher).publishEvent(any(ExportJobRequestedEvent.class));
    }

    @Test
    void nettoyerJobsExpires_passeExpireEtPurgeChampsFichier() {
        when(minioProperties.getBucket()).thenReturn("bucket");

        ExportJob j = new ExportJob();
        j.setId(UUID.randomUUID());
        j.setOrganisationId(orgId);
        j.setDemandePar(userId);
        j.setTypeExport(TypeExport.NOTE_FRAIS_PDF);
        j.setStatut(StatutExportJob.TERMINE);
        j.setProgression(100);
        j.setFichierUrl("http://x");
        j.setNomFichier("x.pdf");
        j.setTailleOctets(123L);
        j.setNbLignes(5);
        HashMap<String, Object> params = new HashMap<>();
        params.put("_objectName", "org/exports/x.pdf");
        j.setParametres(params);

        when(exportJobRepository.findByStatutAndExpireABefore(eq(StatutExportJob.TERMINE), any(Instant.class)))
                .thenReturn(List.of(j));
        when(exportJobRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        exportJobService.nettoyerJobsExpires();

        assertThat(j.getStatut()).isEqualTo(StatutExportJob.EXPIRE);
        assertThat(j.getFichierUrl()).isNull();
        assertThat(j.getNomFichier()).isNull();
        assertThat(j.getTailleOctets()).isNull();
        assertThat(j.getNbLignes()).isNull();
    }

    @Test
    void marquerTermine_metAJourJob() {
        UUID jobId = UUID.randomUUID();
        ExportJob j = new ExportJob();
        j.setId(jobId);
        j.setOrganisationId(orgId);
        j.setDemandePar(userId);
        j.setTypeExport(TypeExport.ETAT_PAIE_PDF);
        j.setStatut(StatutExportJob.EN_COURS);
        j.setProgression(10);

        when(exportJobRepository.findById(jobId)).thenReturn(Optional.of(j));
        when(exportJobRepository.save(any(ExportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        exportJobService.marquerTermine(jobId, "http://url", "file.pdf", 10L, 2);

        assertThat(j.getStatut()).isEqualTo(StatutExportJob.TERMINE);
        assertThat(j.getProgression()).isEqualTo(100);
        assertThat(j.getFichierUrl()).isEqualTo("http://url");
        assertThat(j.getNomFichier()).isEqualTo("file.pdf");
        assertThat(j.getTailleOctets()).isEqualTo(10L);
        assertThat(j.getNbLignes()).isEqualTo(2);
    }
}

