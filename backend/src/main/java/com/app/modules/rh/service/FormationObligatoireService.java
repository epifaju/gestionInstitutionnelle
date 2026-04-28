package com.app.modules.rh.service;

import com.app.modules.rh.dto.FormationObligatoireRequest;
import com.app.modules.rh.dto.FormationObligatoireResponse;
import com.app.modules.rh.entity.EcheanceRh;
import com.app.modules.rh.entity.FormationObligatoire;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutEcheance;
import com.app.modules.rh.entity.TypeEcheance;
import com.app.modules.rh.repository.ConfigAlerteRhRepository;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.FormationObligatoireRepository;
import com.app.modules.rh.repository.SalarieRepository;
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
public class FormationObligatoireService {

    private static final long MAX_FILE = 10_485_760L;

    private final FormationObligatoireRepository formationRepository;
    private final SalarieRepository salarieRepository;
    private final ConfigAlerteRhRepository configRepository;
    private final EcheanceRhRepository echeanceRepository;
    private final MinioStorageService minioStorageService;

    public FormationObligatoireResponse enregistrerFormation(UUID salarieId, FormationObligatoireRequest req, MultipartFile certificat, UUID orgId) {
        Salarie s = salarieRepository.findByIdAndOrganisationId(salarieId, orgId)
                .orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));

        FormationObligatoire f = new FormationObligatoire();
        f.setOrganisationId(orgId);
        f.setSalarie(s);
        f.setIntitule(req.intitule());
        f.setTypeFormation(req.typeFormation());
        f.setOrganisme(req.organisme());
        f.setDateRealisation(req.dateRealisation());
        f.setDateExpiration(req.dateExpiration());
        f.setPeriodiciteMois(req.periodiciteMois());
        f.setNumeroCertificat(req.numeroCertificat());
        f.setCout(req.cout());
        f.setStatut(req.dateRealisation() != null ? "REALISEE" : "A_REALISER");

        if (certificat != null && !certificat.isEmpty()) {
            String obj = uploadPdf(orgId + "/formations/" + salarieId + "/" + UUID.randomUUID() + "/", "certificat.pdf", certificat);
            f.setCertificatUrl(obj);
        }

        formationRepository.save(f);

        if (req.periodiciteMois() != null) {
            int alerteJ = configRepository.findByOrganisationId(orgId).map(x -> x.getAlerteFormationJ()).orElse(60);
            LocalDate dateEcheance = req.dateExpiration().minusDays(alerteJ);
            EcheanceRh e = new EcheanceRh();
            e.setOrganisationId(orgId);
            e.setSalarie(s);
            e.setTypeEcheance(TypeEcheance.FORMATION_OBLIGATOIRE);
            e.setTitre("Renouvellement formation — " + (s.getNom() + " " + s.getPrenom()).trim());
            e.setDateEcheance(dateEcheance);
            e.setStatut(calcInitial(dateEcheance));
            echeanceRepository.save(e);
            f.setEcheanceId(e.getId());
            formationRepository.save(f);
        }

        return toResponse(f);
    }

    public FormationObligatoireResponse renouvelerFormation(UUID id, FormationObligatoireRequest req, MultipartFile certificat, UUID orgId) {
        FormationObligatoire old = formationRepository.findById(id).orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));
        if (!orgId.equals(old.getOrganisationId())) throw BusinessException.notFound("SALARIE_NON_TROUVE");

        // annule l'échéance liée
        if (old.getEcheanceId() != null) {
            echeanceRepository.findByIdAndOrganisationId(old.getEcheanceId(), orgId).ifPresent(e -> {
                e.setStatut(StatutEcheance.ANNULEE);
                echeanceRepository.save(e);
            });
        }

        return enregistrerFormation(old.getSalarie().getId(), req, certificat, orgId);
    }

    @Transactional(readOnly = true)
    public java.util.List<FormationObligatoireResponse> getFormationsSalarie(UUID salarieId, UUID orgId) {
        salarieRepository.findByIdAndOrganisationId(salarieId, orgId).orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));
        return formationRepository.findBySalarieIdOrderByDateExpirationAsc(salarieId).stream()
                .filter(f -> orgId.equals(f.getOrganisationId()))
                .map(this::toResponse)
                .toList();
    }

    private FormationObligatoireResponse toResponse(FormationObligatoire f) {
        Salarie s = f.getSalarie();
        String nom = (s.getNom() + " " + s.getPrenom()).trim();
        long d = ChronoUnit.DAYS.between(LocalDate.now(), f.getDateExpiration());
        int jours = (int) d;
        LocalDateTime created = f.getCreatedAt() != null ? LocalDateTime.ofInstant(f.getCreatedAt(), ZoneId.systemDefault()) : null;
        String url = presignMaybe(f.getCertificatUrl());
        return new FormationObligatoireResponse(
                f.getId(),
                s.getId(),
                nom,
                f.getIntitule(),
                f.getTypeFormation(),
                f.getOrganisme(),
                f.getDateRealisation(),
                f.getDateExpiration(),
                f.getPeriodiciteMois(),
                f.getNumeroCertificat(),
                url,
                f.getStatut(),
                jours,
                f.getCout(),
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

