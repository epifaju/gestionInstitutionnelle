package com.app.modules.rapports.service;

import com.app.modules.rapports.dto.ExportJobResponse;
import com.app.modules.rapports.entity.ExportJob;
import com.app.modules.rapports.entity.StatutExportJob;
import com.app.modules.rapports.entity.TypeExport;
import com.app.modules.rapports.repository.ExportJobRepository;
import com.app.shared.dto.PageResponse;
import com.app.shared.exception.BusinessException;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ExportJobService {

    private final ExportJobRepository exportJobRepository;
    private final ApplicationEventPublisher publisher;

    private final MinioClient minioClient;
    private final com.app.config.MinioProperties minioProperties;

    public ExportJobService(
            ExportJobRepository exportJobRepository,
            ApplicationEventPublisher publisher,
            @Qualifier("internalMinioClient") MinioClient minioClient,
            com.app.config.MinioProperties minioProperties
    ) {
        this.exportJobRepository = exportJobRepository;
        this.publisher = publisher;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public ExportJobResponse creerJob(UUID orgId, UUID userId, TypeExport type, Map<String, Object> params) {
        boolean exists =
                exportJobRepository.existsByOrganisationIdAndTypeExportAndStatutIn(
                        orgId, type, List.of(StatutExportJob.EN_ATTENTE, StatutExportJob.EN_COURS));
        if (exists) {
            throw BusinessException.badRequest("EXPORT_JOB_EN_COURS");
        }

        ExportJob job = new ExportJob();
        job.setOrganisationId(orgId);
        job.setDemandePar(userId);
        job.setTypeExport(type);
        job.setStatut(StatutExportJob.EN_ATTENTE);
        job.setProgression(0);
        job.setParametres(params == null ? new HashMap<>() : new HashMap<>(params));
        // Force immediate insert even if caller transaction is read-only.
        ExportJob saved = exportJobRepository.saveAndFlush(job);

        publisher.publishEvent(new ExportJobRequestedEvent(saved.getId(), orgId, userId, type, saved.getParametres()));
        return toResponse(saved);
    }

    @Transactional
    public void mettreAJourProgression(UUID jobId, int progression) {
        exportJobRepository
                .findById(jobId)
                .ifPresent(
                        j -> {
                            int p = Math.max(0, Math.min(100, progression));
                            j.setProgression(p);
                            if (j.getStatut() == StatutExportJob.EN_ATTENTE) {
                                j.setStatut(StatutExportJob.EN_COURS);
                            }
                            exportJobRepository.save(j);
                        });
    }

    @Transactional
    public void marquerTermine(UUID jobId, String fichierUrl, String nomFichier, long tailleOctets, int nbLignes) {
        ExportJob j =
                exportJobRepository.findById(jobId).orElseThrow(() -> BusinessException.notFound("EXPORT_JOB_NOT_FOUND"));
        j.setStatut(StatutExportJob.TERMINE);
        j.setProgression(100);
        j.setFichierUrl(fichierUrl);
        j.setNomFichier(nomFichier);
        j.setTailleOctets(tailleOctets);
        j.setNbLignes(nbLignes);
        j.setMessageErreur(null);
        exportJobRepository.save(j);
    }

    @Transactional
    public void setObjectName(UUID jobId, String objectName) {
        if (objectName == null || objectName.isBlank()) return;
        exportJobRepository
                .findById(jobId)
                .ifPresent(
                        j -> {
                            Map<String, Object> p = j.getParametres();
                            if (p == null) p = new HashMap<>();
                            p.put("_objectName", objectName);
                            j.setParametres(p);
                            exportJobRepository.save(j);
                        });
    }

    @Transactional
    public void marquerErreur(UUID jobId, String messageErreur) {
        exportJobRepository
                .findById(jobId)
                .ifPresent(
                        j -> {
                            j.setStatut(StatutExportJob.ERREUR);
                            j.setMessageErreur(messageErreur);
                            exportJobRepository.save(j);
                        });
    }

    @Transactional(readOnly = true)
    public ExportJobResponse getJob(UUID jobId, UUID orgId) {
        ExportJob j =
                exportJobRepository
                        .findByIdAndOrganisationId(jobId, orgId)
                        .orElseThrow(() -> BusinessException.notFound("EXPORT_JOB_NOT_FOUND"));
        return toResponse(j);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExportJobResponse> listJobsOrg(UUID orgId, UUID userId, Pageable p) {
        var page = exportJobRepository.findByOrganisationIdAndDemandeParOrderByCreatedAtDesc(orgId, userId, p);
        return PageResponse.from(page, ExportJobService::toResponse);
    }

    @Transactional
    public void annulerJob(UUID jobId, UUID orgId, UUID userId) {
        ExportJob j =
                exportJobRepository
                        .findByIdAndOrganisationId(jobId, orgId)
                        .orElseThrow(() -> BusinessException.notFound("EXPORT_JOB_NOT_FOUND"));
        if (!userId.equals(j.getDemandePar())) {
            throw BusinessException.forbidden("EXPORT_FORBIDDEN");
        }
        if (j.getStatut() != StatutExportJob.EN_ATTENTE) {
            throw BusinessException.badRequest("EXPORT_JOB_NOT_CANCELLABLE");
        }
        j.setStatut(StatutExportJob.ERREUR);
        j.setMessageErreur("Annulé");
        exportJobRepository.save(j);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void nettoyerJobsExpires() {
        Instant now = Instant.now();
        List<ExportJob> jobs = exportJobRepository.findByStatutAndExpireABefore(StatutExportJob.TERMINE, now);
        for (ExportJob j : jobs) {
            try {
                Object objectName = j.getParametres() != null ? j.getParametres().get("_objectName") : null;
                if (objectName instanceof String s && !s.isBlank()) {
                    minioClient.removeObject(
                            RemoveObjectArgs.builder().bucket(minioProperties.getBucket()).object(s).build());
                }
            } catch (Exception e) {
                log.warn("Failed to delete expired export object for job {}", j.getId(), e);
            }
            j.setStatut(StatutExportJob.EXPIRE);
            // Le fichier n'est plus disponible une fois expiré.
            j.setFichierUrl(null);
            j.setNomFichier(null);
            j.setTailleOctets(null);
            j.setNbLignes(null);
            exportJobRepository.save(j);
        }
    }

    public static ExportJobResponse toResponse(ExportJob j) {
        LocalDateTime expireA =
                j.getExpireA() == null ? null : LocalDateTime.ofInstant(j.getExpireA(), ZoneId.systemDefault());
        LocalDateTime createdAt =
                j.getCreatedAt() == null ? null : LocalDateTime.ofInstant(j.getCreatedAt(), ZoneId.systemDefault());
        return new ExportJobResponse(
                j.getId(),
                j.getTypeExport() != null ? j.getTypeExport().name() : null,
                j.getStatut() != null ? j.getStatut().name() : null,
                j.getProgression(),
                j.getFichierUrl(),
                j.getNomFichier(),
                j.getTailleOctets(),
                j.getNbLignes(),
                j.getMessageErreur(),
                expireA,
                createdAt);
    }
}

