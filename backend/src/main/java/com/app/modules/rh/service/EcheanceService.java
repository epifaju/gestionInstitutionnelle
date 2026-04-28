package com.app.modules.rh.service;

import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.rh.dto.EcheanceDashboardResponse;
import com.app.modules.rh.dto.EcheanceRequest;
import com.app.modules.rh.dto.EcheanceResponse;
import com.app.modules.rh.dto.TraiterEcheanceRequest;
import com.app.modules.rh.entity.EcheanceRh;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutEcheance;
import com.app.modules.rh.entity.TypeEcheance;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EcheanceService {

    private static final long MAX_FILE = 10_485_760L;

    private final EcheanceRhRepository echeanceRepository;
    private final SalarieRepository salarieRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MinioStorageService minioStorageService;

    public EcheanceResponse creerEcheance(EcheanceRequest req, UUID orgId, UUID userId) {
        Salarie s = salarieRepository.findByIdAndOrganisationId(req.salarieId(), orgId)
                .orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));

        LocalDate today = LocalDate.now();
        if (!req.dateEcheance().isAfter(today)) {
            throw BusinessException.badRequest("ECHEANCE_DATE_INVALIDE");
        }

        TypeEcheance type;
        try {
            type = TypeEcheance.valueOf(req.typeEcheance().trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw BusinessException.badRequest("ECHEANCE_DATE_INVALIDE");
        }

        List<StatutEcheance> closed = List.of(StatutEcheance.TRAITEE, StatutEcheance.ANNULEE, StatutEcheance.EXPIREE);
        boolean exists = echeanceRepository.existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(
                orgId, s.getId(), type, req.dateEcheance(), closed
        );
        if (exists) throw BusinessException.badRequest("ECHEANCE_DEJA_EXISTANTE");

        EcheanceRh e = new EcheanceRh();
        e.setOrganisationId(orgId);
        e.setSalarie(s);
        e.setContratId(req.contratId());
        e.setTypeEcheance(type);
        e.setTitre(req.titre());
        e.setDescription(req.description());
        e.setDateEcheance(req.dateEcheance());
        e.setPriorite(req.priorite() != null ? req.priorite() : 2);
        e.setResponsableId(req.responsableId());
        e.setCreatedBy(userId);
        e.setStatut(calcStatutInitial(req.dateEcheance(), today));
        echeanceRepository.save(e);

        return toResponse(e);
    }

    public EcheanceResponse traiterEcheance(UUID id, TraiterEcheanceRequest req, MultipartFile preuve, UUID orgId, UUID userId) {
        EcheanceRh e = echeanceRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));

        if (e.getStatut() == StatutEcheance.TRAITEE || e.getStatut() == StatutEcheance.ANNULEE || e.getStatut() == StatutEcheance.EXPIREE) {
            throw BusinessException.badRequest("CONTRAT_STATUT_INVALIDE");
        }

        String url = e.getDocumentPreuveUrl();
        if (preuve != null && !preuve.isEmpty()) {
            String object = uploadPdf(orgId + "/echeances/" + e.getId() + "/", "preuve.pdf", preuve);
            url = object;
        }

        e.setStatut(StatutEcheance.TRAITEE);
        e.setDateTraitement(req.dateTraitement());
        e.setCommentaireTraitement(req.commentaire());
        e.setTraitePar(userId);
        e.setDocumentPreuveUrl(url);
        echeanceRepository.save(e);

        return toResponse(e);
    }

    public EcheanceResponse annulerEcheance(UUID id, String motif, UUID orgId) {
        EcheanceRh e = echeanceRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));
        e.setStatut(StatutEcheance.ANNULEE);
        e.setCommentaireTraitement(motif);
        echeanceRepository.save(e);
        return toResponse(e);
    }

    @Transactional(readOnly = true)
    public Page<EcheanceResponse> listEcheances(UUID orgId, String statut, String type, UUID salarieId, LocalDate dateMin, LocalDate dateMax, Pageable p) {
        StatutEcheance st = parseStatut(statut);
        TypeEcheance ty = parseType(type);
        return echeanceRepository.findByOrganisationIdAndFilters(orgId, st, ty, salarieId, dateMin, dateMax, p).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EcheanceDashboardResponse getDashboard(UUID orgId) {
        List<StatutEcheance> actives = List.of(StatutEcheance.A_VENIR, StatutEcheance.EN_ALERTE, StatutEcheance.ACTION_REQUISE);
        long totalActives = echeanceRepository.countByOrganisationIdAndStatutIn(orgId, actives);

        LocalDate today = LocalDate.now();
        long critiques = echeanceRepository.findEcheancesExpirees(today).size();

        // approx counts by type
        List<StatutEcheance> closed = List.of(StatutEcheance.TRAITEE, StatutEcheance.ANNULEE, StatutEcheance.EXPIREE);
        long finCdd = echeanceRepository.countByOrganisationIdAndTypeEcheanceAndStatutNotIn(orgId, TypeEcheance.FIN_CDD, closed);
        long essai = echeanceRepository.countByOrganisationIdAndTypeEcheanceAndStatutNotIn(orgId, TypeEcheance.FIN_PERIODE_ESSAI, closed);
        long visites = echeanceRepository.countByOrganisationIdAndTypeEcheanceAndStatutNotIn(orgId, TypeEcheance.VISITE_MEDICALE, closed);
        long titres = echeanceRepository.countByOrganisationIdAndTypeEcheanceAndStatutNotIn(orgId, TypeEcheance.TITRE_SEJOUR, closed);
        long formations = echeanceRepository.countByOrganisationIdAndTypeEcheanceAndStatutNotIn(orgId, TypeEcheance.FORMATION_OBLIGATOIRE, closed);

        // top 10
        List<EcheanceResponse> top = echeanceRepository.findTopUrgentes(orgId, PageRequest.of(0, 10)).stream().map(this::toResponse).toList();

        long urgentes = top.stream().filter(x -> "URGENT".equals(x.niveauUrgence())).count();
        long attention = top.stream().filter(x -> "ATTENTION".equals(x.niveauUrgence())).count();

        return new EcheanceDashboardResponse(
                totalActives,
                critiques,
                urgentes,
                attention,
                finCdd,
                essai,
                visites,
                titres,
                formations,
                top
        );
    }

    private EcheanceResponse toResponse(EcheanceRh e) {
        Salarie s = e.getSalarie();
        String salNom = nomComplet(s);
        String responsableNom = nomUtilisateur(e.getResponsableId());
        String traitePar = nomUtilisateur(e.getTraitePar());

        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(today, e.getDateEcheance());
        int joursRestants = (int) days;
        String niveau = urgenceFromDays(days);

        LocalDateTime created = e.getCreatedAt() != null
                ? LocalDateTime.ofInstant(e.getCreatedAt(), ZoneId.systemDefault())
                : null;

        String preuveUrl = presignMaybe(e.getDocumentPreuveUrl());

        return new EcheanceResponse(
                e.getId(),
                s.getId(),
                salNom,
                s.getService(),
                s.getMatricule(),
                e.getTypeEcheance() != null ? e.getTypeEcheance().name() : null,
                e.getTitre(),
                e.getDescription(),
                e.getDateEcheance(),
                e.getStatut() != null ? e.getStatut().name() : null,
                e.getPriorite(),
                responsableNom,
                e.getDateTraitement(),
                e.getCommentaireTraitement(),
                traitePar,
                preuveUrl,
                joursRestants,
                niveau,
                created
        );
    }

    private String nomUtilisateur(UUID id) {
        if (id == null) return null;
        Utilisateur u = utilisateurRepository.findById(id).orElse(null);
        if (u == null) return null;
        String a = u.getNom() == null ? "" : u.getNom().trim();
        String b = u.getPrenom() == null ? "" : u.getPrenom().trim();
        String out = (a + " " + b).trim();
        return out.isBlank() ? u.getEmail() : out;
    }

    private static String nomComplet(Salarie s) {
        if (s == null) return null;
        String a = s.getNom() == null ? "" : s.getNom().trim();
        String b = s.getPrenom() == null ? "" : s.getPrenom().trim();
        String out = (a + " " + b).trim();
        return out.isBlank() ? null : out;
    }

    private static StatutEcheance calcStatutInitial(LocalDate dateEcheance, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, dateEcheance);
        if (days <= 7) return StatutEcheance.ACTION_REQUISE;
        if (days <= 30) return StatutEcheance.EN_ALERTE;
        return StatutEcheance.A_VENIR;
    }

    private static String urgenceFromDays(long days) {
        if (days < 0) return "CRITIQUE";
        if (days <= 7) return "URGENT";
        if (days <= 30) return "ATTENTION";
        return "NORMAL";
    }

    private static StatutEcheance parseStatut(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return StatutEcheance.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    private static TypeEcheance parseType(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return TypeEcheance.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
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
        } catch (BusinessException ex) {
            throw ex;
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

