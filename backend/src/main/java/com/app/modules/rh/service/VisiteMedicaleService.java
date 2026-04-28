package com.app.modules.rh.service;

import com.app.modules.rh.dto.VisiteMedicaleRequest;
import com.app.modules.rh.dto.VisiteMedicaleResponse;
import com.app.modules.rh.entity.EcheanceRh;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutEcheance;
import com.app.modules.rh.entity.StatutVisite;
import com.app.modules.rh.entity.TypeEcheance;
import com.app.modules.rh.entity.VisiteMedicale;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.modules.rh.repository.VisiteMedicaleRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class VisiteMedicaleService {

    private static final long MAX_FILE = 10_485_760L;

    private final VisiteMedicaleRepository visiteRepository;
    private final SalarieRepository salarieRepository;
    private final EcheanceRhRepository echeanceRepository;
    private final MinioStorageService minioStorageService;

    public VisiteMedicaleResponse creerVisite(UUID salarieId, VisiteMedicaleRequest req, UUID orgId) {
        Salarie s = salarieRepository.findByIdAndOrganisationId(salarieId, orgId)
                .orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));

        if (req.dateRealisee() != null && req.datePlanifiee() != null && req.dateRealisee().isBefore(req.datePlanifiee())) {
            throw BusinessException.badRequest("ECHEANCE_DATE_INVALIDE");
        }

        VisiteMedicale v = new VisiteMedicale();
        v.setOrganisationId(orgId);
        v.setSalarie(s);
        v.setTypeVisite(req.typeVisite());
        v.setDatePlanifiee(req.datePlanifiee());
        v.setDateRealisee(req.dateRealisee());
        v.setMedecin(req.medecin());
        v.setCentreMedical(req.centreMedical());
        v.setResultat(req.resultat());
        v.setRestrictions(req.restrictions());
        v.setPeriodiciteMois(req.periodiciteMois() != null ? req.periodiciteMois() : 24);

        if (req.dateRealisee() != null && v.getPeriodiciteMois() != null && v.getPeriodiciteMois() > 0) {
            LocalDate prochaine = req.dateRealisee().plusMonths(v.getPeriodiciteMois());
            v.setProchaineVisite(prochaine);
            // créer une échéance de suivi
            EcheanceRh e = new EcheanceRh();
            e.setOrganisationId(orgId);
            e.setSalarie(s);
            e.setTypeEcheance(TypeEcheance.VISITE_MEDICALE);
            e.setTitre("Visite médicale " + req.typeVisite() + " — " + (s.getNom() + " " + s.getPrenom()).trim());
            e.setDateEcheance(prochaine);
            e.setStatut(calcInitial(prochaine));
            echeanceRepository.save(e);
            v.setEcheanceId(e.getId());
        }

        visiteRepository.save(v);
        return toResponse(v);
    }

    public VisiteMedicaleResponse enregistrerResultat(UUID id, String resultat, String restrictions, MultipartFile compteRendu, UUID orgId) {
        VisiteMedicale v = visiteRepository.findById(id).orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));
        if (!orgId.equals(v.getOrganisationId())) throw BusinessException.forbidden("SALARIE_NON_TROUVE");

        v.setStatut(StatutVisite.REALISEE);
        v.setResultat(resultat);
        v.setRestrictions(restrictions);

        if (compteRendu != null && !compteRendu.isEmpty()) {
            String obj = uploadPdf(orgId + "/visites-medicales/" + v.getSalarie().getId() + "/" + v.getId() + "/", "compte-rendu.pdf", compteRendu);
            v.setCompteRenduUrl(obj);
        }
        visiteRepository.save(v);

        if (v.getEcheanceId() != null) {
            echeanceRepository.findByIdAndOrganisationId(v.getEcheanceId(), orgId).ifPresent(e -> {
                e.setStatut(StatutEcheance.TRAITEE);
                echeanceRepository.save(e);
            });
        }

        return toResponse(v);
    }

    @Transactional(readOnly = true)
    public java.util.List<VisiteMedicaleResponse> getVisitesSalarie(UUID salarieId, UUID orgId) {
        salarieRepository.findByIdAndOrganisationId(salarieId, orgId).orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));
        return visiteRepository.findBySalarieIdOrderByDateRealiseeDesc(salarieId).stream()
                .filter(v -> orgId.equals(v.getOrganisationId()))
                .map(this::toResponse)
                .toList();
    }

    private VisiteMedicaleResponse toResponse(VisiteMedicale v) {
        Salarie s = v.getSalarie();
        String nom = (s.getNom() + " " + s.getPrenom()).trim();
        LocalDateTime created = v.getCreatedAt() != null ? LocalDateTime.ofInstant(v.getCreatedAt(), ZoneId.systemDefault()) : null;
        String url = presignMaybe(v.getCompteRenduUrl());
        return new VisiteMedicaleResponse(
                v.getId(),
                s.getId(),
                nom,
                v.getTypeVisite(),
                v.getDatePlanifiee(),
                v.getDateRealisee(),
                v.getMedecin(),
                v.getCentreMedical(),
                v.getStatut() != null ? v.getStatut().name() : null,
                v.getResultat(),
                v.getRestrictions(),
                v.getProchaineVisite(),
                v.getPeriodiciteMois(),
                url,
                created
        );
    }

    private static StatutEcheance calcInitial(LocalDate date) {
        long d = ChronoUnit.DAYS.between(LocalDate.now(), date);
        if (d <= 7) return StatutEcheance.ACTION_REQUISE;
        if (d <= 30) return StatutEcheance.EN_ALERTE;
        return StatutEcheance.A_VENIR;
    }

    private String uploadPdf(String prefix, String filename, MultipartFile file) {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("DOCUMENT_REQUIS");
        if (file.getSize() > MAX_FILE) throw BusinessException.badRequest("FICHIER_TROP_GRAND");
        try {
            byte[] bytes = file.getBytes();
            String mime = new Tika().detect(bytes, file.getOriginalFilename());
            if (!"application/pdf".equals(mime)) throw BusinessException.badRequest("FICHIER_TYPE_INVALIDE");
            String objectName = prefix + filename;
            minioStorageService.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, "application/pdf");
            return objectName;
        } catch (Exception ex) {
            throw BusinessException.badRequest("UPLOAD_ECHEC");
        }
    }

    private String presignMaybe(String objectName) {
        if (objectName == null || objectName.isBlank()) return null;
        if (objectName.startsWith("http://") || objectName.startsWith("https://")) return objectName;
        try {
            return minioStorageService.presignedGetUrl(objectName);
        } catch (Exception e) {
            return objectName;
        }
    }
}

