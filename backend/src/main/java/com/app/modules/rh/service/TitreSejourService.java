package com.app.modules.rh.service;

import com.app.modules.rh.dto.TitreSejourRequest;
import com.app.modules.rh.dto.TitreSejourResponse;
import com.app.modules.rh.entity.EcheanceRh;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutEcheance;
import com.app.modules.rh.entity.TitreSejour;
import com.app.modules.rh.entity.TypeEcheance;
import com.app.modules.rh.repository.ConfigAlerteRhRepository;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.modules.rh.repository.TitreSejourRepository;
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
public class TitreSejourService {

    private static final long MAX_FILE = 10_485_760L;

    private final TitreSejourRepository titreRepository;
    private final SalarieRepository salarieRepository;
    private final EcheanceRhRepository echeanceRepository;
    private final ConfigAlerteRhRepository configRepository;
    private final MinioStorageService minioStorageService;

    public TitreSejourResponse enregistrerTitre(UUID salarieId, TitreSejourRequest req, MultipartFile document, UUID orgId) {
        Salarie s = salarieRepository.findByIdAndOrganisationId(salarieId, orgId)
                .orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));

        TitreSejour t = new TitreSejour();
        t.setOrganisationId(orgId);
        t.setSalarie(s);
        t.setTypeDocument(req.typeDocument());
        t.setNumeroDocument(req.numeroDocument());
        t.setPaysEmetteur(req.paysEmetteur());
        t.setDateEmission(req.dateEmission());
        t.setDateExpiration(req.dateExpiration());
        t.setAutoriteEmettrice(req.autoriteEmettrice());

        if (document != null && !document.isEmpty()) {
            String obj = uploadPdf(orgId + "/titres-sejour/" + salarieId + "/" + UUID.randomUUID() + "/", "document.pdf", document);
            t.setDocumentUrl(obj);
        }

        titreRepository.save(t);

        int alerteJ = configRepository.findByOrganisationId(orgId).map(x -> x.getAlerteTitreSejourJ()).orElse(90);
        LocalDate dateEcheance = req.dateExpiration().minusDays(alerteJ);
        EcheanceRh e = new EcheanceRh();
        e.setOrganisationId(orgId);
        e.setSalarie(s);
        e.setTypeEcheance(TypeEcheance.TITRE_SEJOUR);
        e.setTitre("Expiration " + req.typeDocument() + " — " + (s.getNom() + " " + s.getPrenom()).trim());
        e.setDateEcheance(dateEcheance);
        e.setStatut(calcInitial(dateEcheance));
        echeanceRepository.save(e);

        t.setEcheanceId(e.getId());
        titreRepository.save(t);

        return toResponse(t);
    }

    public TitreSejourResponse mettreAJourStatutRenouvellement(UUID id, String statut, UUID orgId) {
        TitreSejour t = titreRepository.findById(id).orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));
        if (!orgId.equals(t.getOrganisationId())) throw BusinessException.notFound("SALARIE_NON_TROUVE");
        t.setStatutRenouvellement(statut);
        titreRepository.save(t);
        return toResponse(t);
    }

    @Transactional(readOnly = true)
    public java.util.List<TitreSejourResponse> getTitresSalarie(UUID salarieId, UUID orgId) {
        salarieRepository.findByIdAndOrganisationId(salarieId, orgId).orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));
        return titreRepository.findBySalarieIdOrderByDateExpirationAsc(salarieId).stream()
                .filter(t -> orgId.equals(t.getOrganisationId()))
                .map(this::toResponse)
                .toList();
    }

    private TitreSejourResponse toResponse(TitreSejour t) {
        Salarie s = t.getSalarie();
        String nom = (s.getNom() + " " + s.getPrenom()).trim();
        LocalDate today = LocalDate.now();
        long d = ChronoUnit.DAYS.between(today, t.getDateExpiration());
        int jours = (int) d;
        String niveau = niveauUrgence(d);
        LocalDateTime created = t.getCreatedAt() != null ? LocalDateTime.ofInstant(t.getCreatedAt(), ZoneId.systemDefault()) : null;
        String url = presignMaybe(t.getDocumentUrl());
        return new TitreSejourResponse(
                t.getId(),
                s.getId(),
                nom,
                t.getTypeDocument(),
                t.getNumeroDocument(),
                t.getPaysEmetteur(),
                t.getDateEmission(),
                t.getDateExpiration(),
                t.getAutoriteEmettrice(),
                url,
                t.getStatutRenouvellement(),
                jours,
                niveau,
                created
        );
    }

    private static String niveauUrgence(long days) {
        if (days < 0) return "CRITIQUE";
        if (days <= 7) return "URGENT";
        if (days <= 30) return "ATTENTION";
        return "NORMAL";
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

