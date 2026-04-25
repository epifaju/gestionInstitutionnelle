package com.app.modules.rh.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.dto.DocumentUrlResponse;
import com.app.modules.rh.dto.DroitsCongesDto;
import com.app.modules.rh.dto.HistoriqueSalaireResponse;
import com.app.modules.rh.dto.SalaireActuel;
import com.app.modules.rh.dto.SalarieRequest;
import com.app.modules.rh.dto.SalarieResponse;
import com.app.modules.rh.entity.DroitsConges;
import com.app.modules.rh.entity.HistoriqueSalaire;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutSalarie;
import com.app.modules.rh.repository.DroitsCongesRepository;
import com.app.modules.rh.repository.HistoriqueSalaireRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.modules.rh.repository.SalarieSpecifications;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@Service
@RequiredArgsConstructor
public class SalarieService {

    private static final long MAX_FILE = 10_485_760L;
    private static final Pattern SAFE_NAME = Pattern.compile("[^a-zA-Z0-9._-]");

    private final SalarieRepository salarieRepository;
    private final HistoriqueSalaireRepository historiqueSalaireRepository;
    private final DroitsCongesRepository droitsCongesRepository;
    private final AuditLogService auditLogService;
    private final MinioStorageService minioStorageService;

    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    @Transactional(readOnly = true)
    public Page<SalarieResponse> listSalaries(
            UUID orgId, String statut, String service, String search, Pageable pageable) {
        StatutSalarie st = parseStatut(statut);
        String svc = blankToNull(service);
        String q = search == null ? "" : search.trim();

        Specification<Salarie> spec =
                Specification.where(SalarieSpecifications.organisationId(orgId))
                        .and(SalarieSpecifications.statutOptional(st))
                        .and(SalarieSpecifications.serviceOptional(svc))
                        .and(SalarieSpecifications.searchOptional(q));

        Pageable sorted =
                pageable.getSort().isSorted()
                        ? pageable
                        : PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                Sort.by(Sort.Order.desc("createdAt")));

        return salarieRepository.findAll(spec, sorted).map(s -> toResponse(s, orgId));
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN') or @securityService.isSelf(#id,authentication)")
    @Transactional(readOnly = true)
    public SalarieResponse getById(UUID id, UUID orgId) {
        Salarie s = loadOwned(id, orgId);
        return toResponse(s, orgId);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public SalarieResponse getMe(UUID orgId, UUID userId, String email) {
        Salarie s = salarieRepository
                .findByOrganisationIdAndUtilisateur_Id(orgId, userId)
                .orElseGet(() -> {
                    if (email == null || email.isBlank()) return null;
                    return salarieRepository.findByOrganisationIdAndEmailIgnoreCase(orgId, email.trim()).orElse(null);
                });
        if (s == null) {
            throw BusinessException.notFound("SALARIE_ABSENT");
        }
        return toResponse(s, orgId);
    }

    /**
     * Version stricte pour le portail employé: uniquement via le lien utilisateur_id.
     * (Pas de fallback email)
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public SalarieResponse getMeStrict(UUID orgId, UUID userId) {
        Salarie s = salarieRepository
                .findByOrganisationIdAndUtilisateur_Id(orgId, userId)
                .orElse(null);
        if (s == null) {
            throw BusinessException.notFound("SALARIE_NON_LIE");
        }
        return toResponse(s, orgId);
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    @Transactional
    public SalarieResponse creer(SalarieRequest req, UUID orgId) {
        UUID userId = currentUserId();
        long seq = salarieRepository.countByOrganisationId(orgId);
        String matricule = "EMP-" + String.format("%04d", seq + 1);

        Salarie s = new Salarie();
        s.setOrganisationId(orgId);
        s.setMatricule(matricule);
        applyRequest(s, req);
        s.setStatut(StatutSalarie.BROUILLON);
        s = salarieRepository.save(s);

        HistoriqueSalaire h = new HistoriqueSalaire();
        h.setSalarie(s);
        h.setMontantBrut(req.montantBrut());
        h.setMontantNet(req.montantNet());
        h.setDevise(req.devise());
        h.setDateDebut(req.dateEmbauche());
        h.setDateFin(null);
        historiqueSalaireRepository.save(h);

        int annee = LocalDate.now().getYear();
        DroitsConges dc = new DroitsConges();
        dc.setOrganisationId(orgId);
        dc.setSalarie(s);
        dc.setAnnee(annee);
        dc.setJoursDroit(new BigDecimal("30"));
        dc.setJoursPris(BigDecimal.ZERO);
        dc.setJoursRestants(new BigDecimal("30"));
        droitsCongesRepository.save(dc);

        auditLogService.logRh(orgId, userId, "CREATE", "Salarie", s.getId(), null, snapshotSalarie(s, h));
        return toResponse(s, orgId);
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    @Transactional
    public SalarieResponse modifier(UUID id, SalarieRequest req, UUID orgId) {
        UUID userId = currentUserId();
        Salarie s = loadOwned(id, orgId);
        HistoriqueSalaire hOpen =
                historiqueSalaireRepository
                        .findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(id)
                        .orElseThrow(() -> BusinessException.notFound("HISTORIQUE_SALAIRE_ABSENT"));
        Map<String, Object> avant = snapshotSalarie(s, hOpen);

        // PRD v3 (§8.1 Historique salaires) : tout changement de rémunération doit créer une nouvelle grille,
        // pas modifier la grille en cours. L'UI dispose déjà de "Nouvelle grille" avec une date de début.
        if (hOpen.getMontantBrut().compareTo(req.montantBrut()) != 0
                || hOpen.getMontantNet().compareTo(req.montantNet()) != 0
                || !Objects.equals(hOpen.getDevise(), req.devise())) {
            throw BusinessException.badRequest("SALAIRE_MODIF_VIA_GRILLE");
        }

        applyRequest(s, req);
        salarieRepository.save(s);

        Map<String, Object> apres = snapshotSalarie(s, hOpen);
        auditLogService.logRh(orgId, userId, "UPDATE", "Salarie", s.getId(), avant, apres);
        return toResponse(s, orgId);
    }

    @PreAuthorize("hasRole('RH')")
    @Transactional
    public SalarieResponse validerDossier(UUID id, UUID orgId) {
        UUID userId = currentUserId();
        Salarie s = loadOwned(id, orgId);
        if (s.getStatut() != StatutSalarie.BROUILLON) {
            throw BusinessException.badRequest("DOSSIER_DEJA_VALIDE");
        }
        Map<String, Object> avant = snapshotSalarie(s, currentHistoriqueOrThrow(id));
        s.setStatut(StatutSalarie.ACTIF);
        salarieRepository.save(s);
        HistoriqueSalaire h = currentHistoriqueOrThrow(id);
        auditLogService.logRh(orgId, userId, "UPDATE", "Salarie", s.getId(), avant, snapshotSalarie(s, h));
        return toResponse(s, orgId);
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    @Transactional
    public String uploadContrat(UUID id, MultipartFile file, UUID orgId) throws Exception {
        UUID userId = currentUserId();
        Salarie s = loadOwned(id, orgId);
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("FICHIER_MANQUANT");
        }
        if (file.getSize() > MAX_FILE) {
            throw BusinessException.badRequest("FICHIER_TROP_GRAND");
        }
        byte[] bytes = file.getBytes();
        String mime = new Tika().detect(bytes, file.getOriginalFilename());
        if (!"application/pdf".equals(mime)) {
            throw BusinessException.badRequest("FICHIER_TYPE_INVALIDE");
        }
        String rawName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "contrat.pdf";
        String safe = SAFE_NAME.matcher(rawName).replaceAll("_");
        String objectName = "contrats/" + id + "/" + safe;
        minioStorageService.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, mime);
        auditLogService.logRh(
                orgId,
                userId,
                "UPDATE",
                "Salarie",
                s.getId(),
                null,
                Map.of("action", "UPLOAD_CONTRAT", "object", objectName));
        return "/api/v1/rh/salaries/" + id + "/documents/" + safe;
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    @Transactional(readOnly = true)
    public List<DocumentUrlResponse> listDocuments(UUID id, UUID orgId) throws Exception {
        loadOwned(id, orgId);
        String prefix = "contrats/" + id + "/";
        List<String> names = minioStorageService.listObjectNames(prefix);
        List<DocumentUrlResponse> out = new java.util.ArrayList<>();
        for (String objectName : names) {
            String nom = objectName.substring(objectName.lastIndexOf('/') + 1);
            out.add(new DocumentUrlResponse(nom, "/api/v1/rh/salaries/" + id + "/documents/" + nom));
        }
        return out;
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    @Transactional(readOnly = true)
    public MinioStorageService.Download downloadContrat(UUID salarieId, String filename, UUID orgId) throws Exception {
        loadOwned(salarieId, orgId);
        if (filename == null || filename.isBlank()) {
            throw BusinessException.badRequest("FICHIER_MANQUANT");
        }
        String safe = SAFE_NAME.matcher(filename).replaceAll("_");
        String objectName = "contrats/" + salarieId + "/" + safe;
        return minioStorageService.download(objectName);
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    @Transactional(readOnly = true)
    public List<HistoriqueSalaireResponse> listHistoriqueSalaires(UUID id, UUID orgId) {
        loadOwned(id, orgId);
        return historiqueSalaireRepository.findBySalarie_IdOrderByDateDebutDesc(id).stream()
                .map(
                        h ->
                                new HistoriqueSalaireResponse(
                                        h.getMontantBrut(),
                                        h.getMontantNet(),
                                        h.getDevise(),
                                        h.getDateDebut(),
                                        h.getDateFin()))
                .toList();
    }

    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    @Transactional
    public SalarieResponse ajouterGrilleSalariale(
            UUID id, BigDecimal brut, BigDecimal net, String devise, LocalDate dateDebut, UUID orgId) {
        UUID userId = currentUserId();
        Salarie s = loadOwned(id, orgId);
        HistoriqueSalaire courant =
                historiqueSalaireRepository
                        .findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(id)
                        .orElseThrow(() -> BusinessException.notFound("HISTORIQUE_SALAIRE_ABSENT"));
        Map<String, Object> avant = snapshotSalarie(s, courant);
        if (!dateDebut.isAfter(courant.getDateDebut())) {
            throw BusinessException.badRequest("DATE_GRILLE_INVALIDE");
        }
        courant.setDateFin(dateDebut.minusDays(1));
        historiqueSalaireRepository.save(courant);

        HistoriqueSalaire neu = new HistoriqueSalaire();
        neu.setSalarie(s);
        neu.setMontantBrut(brut);
        neu.setMontantNet(net);
        neu.setDevise(devise);
        neu.setDateDebut(dateDebut);
        neu.setDateFin(null);
        historiqueSalaireRepository.save(neu);

        auditLogService.logRh(orgId, userId, "UPDATE", "Salarie", s.getId(), avant, snapshotSalarie(s, neu));
        return toResponse(s, orgId);
    }

    private HistoriqueSalaire currentHistoriqueOrThrow(UUID salarieId) {
        return historiqueSalaireRepository
                .findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(salarieId)
                .orElseThrow(() -> BusinessException.notFound("HISTORIQUE_SALAIRE_ABSENT"));
    }

    private Salarie loadOwned(UUID id, UUID orgId) {
        Salarie s = salarieRepository.findById(id).orElseThrow(() -> BusinessException.notFound("SALARIE_ABSENT"));
        if (!s.getOrganisationId().equals(orgId)) {
            throw BusinessException.forbidden("SALARIE_ORG_MISMATCH");
        }
        return s;
    }

    private void applyRequest(Salarie s, SalarieRequest req) {
        s.setNom(req.nom());
        s.setPrenom(req.prenom());
        s.setEmail(req.email());
        s.setTelephone(req.telephone());
        s.setPoste(req.poste());
        s.setService(req.service());
        s.setDateEmbauche(req.dateEmbauche());
        s.setTypeContrat(req.typeContrat());
        s.setNationalite(req.nationalite());
        s.setAdresse(req.adresse());
    }

    private SalarieResponse toResponse(Salarie s, UUID orgId) {
        HistoriqueSalaire h =
                historiqueSalaireRepository
                        .findTopBySalarie_IdAndDateFinIsNullOrderByDateDebutDesc(s.getId())
                        .orElseGet(
                                () ->
                                        historiqueSalaireRepository
                                                .findBySalarie_IdOrderByDateDebutDesc(s.getId())
                                                .stream()
                                                .findFirst()
                                                .orElse(null));
        SalaireActuel sa =
                h == null
                        ? null
                        : new SalaireActuel(h.getMontantBrut(), h.getMontantNet(), h.getDevise(), h.getDateDebut());
        int annee = LocalDate.now().getYear();
        DroitsCongesDto droits =
                droitsCongesRepository
                        .findBySalarie_IdAndAnnee(s.getId(), annee)
                        .map(
                                d ->
                                        new DroitsCongesDto(
                                                d.getAnnee(),
                                                d.getJoursDroit(),
                                                d.getJoursPris(),
                                                d.getJoursRestants()))
                        .orElse(null);
        LocalDateTime created =
                s.getCreatedAt() == null
                        ? null
                        : LocalDateTime.ofInstant(s.getCreatedAt(), ZoneId.systemDefault());
        return new SalarieResponse(
                s.getId(),
                s.getMatricule(),
                s.getNom(),
                s.getPrenom(),
                s.getEmail(),
                s.getTelephone(),
                s.getPoste(),
                s.getService(),
                s.getDateEmbauche(),
                s.getTypeContrat(),
                s.getStatut().name(),
                s.getNationalite(),
                s.getAdresse(),
                sa,
                droits,
                created);
    }

    private Map<String, Object> snapshotSalarie(Salarie s, HistoriqueSalaire h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("matricule", s.getMatricule());
        m.put("nom", s.getNom());
        m.put("prenom", s.getPrenom());
        m.put("email", s.getEmail());
        m.put("poste", s.getPoste());
        m.put("service", s.getService());
        m.put("statut", s.getStatut() != null ? s.getStatut().name() : null);
        if (h != null) {
            m.put("montantBrut", h.getMontantBrut());
            m.put("montantNet", h.getMontantNet());
            m.put("devise", h.getDevise());
            m.put("dateDebutGrille", h.getDateDebut());
            m.put("dateFinGrille", h.getDateFin());
        }
        return m;
    }

    private static StatutSalarie parseStatut(String statut) {
        if (statut == null || statut.isBlank()) {
            return null;
        }
        try {
            return StatutSalarie.valueOf(statut.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails ud)) {
            throw BusinessException.unauthorized("TOKEN_INVALIDE");
        }
        return ud.getId();
    }
}
