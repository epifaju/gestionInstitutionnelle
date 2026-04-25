package com.app.modules.missions.service;

import com.app.modules.finance.dto.FactureRequest;
import com.app.modules.finance.service.FactureService;
import com.app.modules.finance.service.TauxChangeService;
import com.app.modules.missions.dto.FraisRequest;
import com.app.modules.missions.dto.FraisResponse;
import com.app.modules.missions.dto.MissionRequest;
import com.app.modules.missions.dto.MissionResponse;
import com.app.modules.missions.entity.FraisMission;
import com.app.modules.missions.entity.Mission;
import com.app.modules.missions.entity.StatutFrais;
import com.app.modules.missions.entity.StatutMission;
import com.app.modules.missions.repository.FraisMissionRepository;
import com.app.modules.missions.repository.MissionRepository;
import com.app.modules.missions.repository.MissionSpecifications;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MissionService {

    private static final long MAX_FILE = 10_485_760L;

    private final MissionRepository missionRepository;
    private final FraisMissionRepository fraisMissionRepository;
    private final SalarieRepository salarieRepository;
    private final MinioStorageService minioStorageService;
    private final TauxChangeService tauxChangeService;
    private final FactureService factureService;

    @Transactional(readOnly = true)
    public Page<MissionResponse> list(UUID orgId, String statut, UUID salarieId, LocalDate debut, LocalDate fin, Pageable pageable) {
        StatutMission st = parseStatut(statut);
        Specification<Mission> spec = Specification.where(MissionSpecifications.organisationId(orgId))
                .and(MissionSpecifications.statutOptional(st))
                .and(MissionSpecifications.salarieOptional(salarieId))
                .and(MissionSpecifications.dateDebutOptional(debut))
                .and(MissionSpecifications.dateFinOptional(fin));
        return missionRepository.findAll(spec, pageable).map(this::toResponseLight);
    }

    @Transactional(readOnly = true)
    public MissionResponse getById(UUID id, UUID orgId) {
        Mission m = loadOwned(id, orgId);
        List<FraisMission> frais = fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(id);
        return toResponse(m, frais);
    }

    @Transactional
    public MissionResponse creer(MissionRequest req, UUID orgId, UUID salarieId) {
        Salarie s = salarieRepository.findById(salarieId).orElseThrow(() -> BusinessException.notFound("SALARIE_ABSENT"));
        if (!orgId.equals(s.getOrganisationId())) throw BusinessException.forbidden("SALARIE_ORG_MISMATCH");
        if (req.dateRetour().isBefore(req.dateDepart())) throw BusinessException.badRequest("DATES_MISSION_INVALIDES");

        Mission m = new Mission();
        m.setOrganisationId(orgId);
        m.setSalarie(s);
        apply(m, req);
        m.setStatut(StatutMission.BROUILLON);
        m.setUpdatedAt(Instant.now());
        m = missionRepository.save(m);
        return getById(m.getId(), orgId);
    }

    @Transactional
    public MissionResponse update(UUID id, MissionRequest req, UUID orgId, UUID actorSalarieId) {
        Mission m = loadOwned(id, orgId);
        if (m.getStatut() != StatutMission.BROUILLON) {
            throw BusinessException.badRequest("MISSION_NON_MODIFIABLE");
        }
        if (!m.getSalarie().getId().equals(actorSalarieId)) {
            throw BusinessException.forbidden("MISSION_NOT_OWNER");
        }
        apply(m, req);
        m.setUpdatedAt(Instant.now());
        missionRepository.save(m);
        return getById(id, orgId);
    }

    @Transactional
    public MissionResponse soumettre(UUID id, UUID orgId, UUID actorSalarieId) {
        Mission m = loadOwned(id, orgId);
        if (m.getStatut() != StatutMission.BROUILLON) throw BusinessException.badRequest("MISSION_STATUT_INVALIDE");
        if (!m.getSalarie().getId().equals(actorSalarieId)) throw BusinessException.forbidden("MISSION_NOT_OWNER");
        m.setStatut(StatutMission.SOUMISE);
        m.setUpdatedAt(Instant.now());
        missionRepository.save(m);
        return getById(id, orgId);
    }

    @Transactional
    public MissionResponse approuver(UUID id, UUID approbateurId, BigDecimal avanceVersee, UUID orgId) {
        Mission m = loadOwned(id, orgId);
        if (m.getStatut() != StatutMission.SOUMISE) throw BusinessException.badRequest("MISSION_STATUT_INVALIDE");
        m.setStatut(StatutMission.APPROUVEE);
        m.setApprobateurId(approbateurId);
        m.setDateApprobation(Instant.now());
        if (avanceVersee != null && avanceVersee.compareTo(BigDecimal.ZERO) > 0) {
            m.setAvanceVersee(avanceVersee);
            // Create invoice as expense trace (A_PAYER) for the advance
            FactureRequest fr = new FactureRequest(
                    fournisseurFor(m.getSalarie()),
                    LocalDate.now(),
                    avanceVersee,
                    BigDecimal.ZERO,
                    normalizeDevise(m.getAvanceDevise()),
                    null,
                    "A_PAYER",
                    "Avance mission: " + m.getTitre()
            );
            try {
                factureService.creer(fr, null, orgId, approbateurId);
            } catch (Exception e) {
                throw BusinessException.badRequest("MISSION_AVANCE_FACTURE_ERREUR");
            }
        }
        m.setUpdatedAt(Instant.now());
        missionRepository.save(m);
        return getById(id, orgId);
    }

    @Transactional
    public MissionResponse refuser(UUID id, String motifRefus, UUID orgId) {
        Mission m = loadOwned(id, orgId);
        if (m.getStatut() != StatutMission.SOUMISE) throw BusinessException.badRequest("MISSION_STATUT_INVALIDE");
        m.setStatut(StatutMission.ANNULEE);
        m.setMotifRefus(motifRefus);
        m.setUpdatedAt(Instant.now());
        missionRepository.save(m);
        return getById(id, orgId);
    }

    @Transactional
    public MissionResponse terminer(UUID id, UUID orgId) {
        Mission m = loadOwned(id, orgId);
        if (m.getStatut() != StatutMission.EN_COURS && m.getStatut() != StatutMission.APPROUVEE) {
            throw BusinessException.badRequest("MISSION_STATUT_INVALIDE");
        }
        m.setStatut(StatutMission.TERMINEE);
        m.setUpdatedAt(Instant.now());
        missionRepository.save(m);
        return getById(id, orgId);
    }

    @Transactional
    public String uploadOrdreMission(UUID id, MultipartFile file, UUID orgId) throws Exception {
        Mission m = loadOwned(id, orgId);
        String objectName = uploadPdf("missions/" + orgId + "/" + id + "/ordre/", file);
        m.setOrdreMissionUrl(objectName);
        m.setUpdatedAt(Instant.now());
        missionRepository.save(m);
        return "/api/v1/missions/" + id + "/ordre-mission";
    }

    @Transactional
    public String uploadRapport(UUID id, MultipartFile file, UUID orgId) throws Exception {
        Mission m = loadOwned(id, orgId);
        String objectName = uploadPdf("missions/" + orgId + "/" + id + "/rapport/", file);
        m.setRapportUrl(objectName);
        m.setUpdatedAt(Instant.now());
        missionRepository.save(m);
        return "/api/v1/missions/" + id + "/rapport";
    }

    @Transactional
    public FraisResponse ajouterFrais(UUID missionId, FraisRequest req, MultipartFile justificatif, UUID orgId) throws Exception {
        Mission m = loadOwned(missionId, orgId);
        if (req.dateFrais().isBefore(m.getDateDepart()) || req.dateFrais().isAfter(m.getDateRetour())) {
            throw BusinessException.badRequest("DATE_FRAIS_HORS_MISSION");
        }
        BigDecimal taux = tauxChangeService.tauxVersEur(orgId, req.devise(), req.dateFrais());
        FraisMission f = new FraisMission();
        f.setMission(m);
        f.setTypeFrais(req.typeFrais());
        f.setDescription(req.description());
        f.setDateFrais(req.dateFrais());
        f.setMontant(req.montant());
        f.setDevise(normalizeDevise(req.devise()));
        f.setTauxChangeEur(taux);
        f.setStatut(StatutFrais.SOUMIS);

        if (justificatif != null && !justificatif.isEmpty()) {
            String objectName = uploadAny("missions/" + orgId + "/" + missionId + "/frais/" + UUID.randomUUID() + "/", justificatif);
            f.setJustificatifUrl(objectName);
        }
        fraisMissionRepository.save(f);
        return toFraisResponse(f);
    }

    @Transactional
    public FraisResponse validerFrais(UUID missionId, UUID fraisId, UUID orgId) {
        Mission m = loadOwned(missionId, orgId);
        FraisMission f = fraisMissionRepository.findByIdAndMission_Id(fraisId, missionId)
                .orElseThrow(() -> BusinessException.notFound("FRAIS_ABSENT"));
        if (f.getStatut() != StatutFrais.SOUMIS) throw BusinessException.badRequest("FRAIS_STATUT_INVALIDE");
        f.setStatut(StatutFrais.VALIDE);
        fraisMissionRepository.save(f);
        // If mission approved, consider it in progress
        if (m.getStatut() == StatutMission.APPROUVEE) {
            m.setStatut(StatutMission.EN_COURS);
            m.setUpdatedAt(Instant.now());
            missionRepository.save(m);
        }
        return toFraisResponse(f);
    }

    @Transactional
    public FraisResponse rembourserFrais(UUID missionId, UUID fraisId, UUID orgId, UUID actorId) {
        FraisMission f = fraisMissionRepository.findByIdAndMission_Id(fraisId, missionId)
                .orElseThrow(() -> BusinessException.notFound("FRAIS_ABSENT"));
        Mission m = loadOwned(missionId, orgId);
        if (f.getStatut() != StatutFrais.VALIDE) throw BusinessException.badRequest("FRAIS_STATUT_INVALIDE");
        f.setStatut(StatutFrais.REMBOURSE);
        fraisMissionRepository.save(f);

        // Create invoice line as expense trace for reimbursement
        BigDecimal montant = f.getMontant();
        FactureRequest fr = new FactureRequest(
                fournisseurFor(m.getSalarie()),
                LocalDate.now(),
                montant,
                BigDecimal.ZERO,
                normalizeDevise(f.getDevise()),
                null,
                "A_PAYER",
                "Remboursement frais mission: " + m.getTitre() + " — " + f.getTypeFrais()
        );
        try {
            factureService.creer(fr, null, orgId, actorId);
        } catch (Exception e) {
            throw BusinessException.badRequest("MISSION_REMBOURSEMENT_FACTURE_ERREUR");
        }

        return toFraisResponse(f);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculerTotalFrais(UUID missionId, UUID orgId) {
        loadOwned(missionId, orgId);
        List<FraisMission> list = fraisMissionRepository.findByMission_IdAndStatut(missionId, StatutFrais.VALIDE);
        BigDecimal sum = BigDecimal.ZERO;
        for (FraisMission f : list) {
            sum = sum.add(f.getMontantEur());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculerSolde(UUID missionId, UUID orgId) {
        Mission m = loadOwned(missionId, orgId);
        BigDecimal total = calculerTotalFrais(missionId, orgId);
        BigDecimal avance = m.getAvanceVersee() != null ? m.getAvanceVersee() : BigDecimal.ZERO;
        return total.subtract(avance).setScale(2, RoundingMode.HALF_UP);
    }

    private Mission loadOwned(UUID id, UUID orgId) {
        Mission m = missionRepository.findById(id).orElseThrow(() -> BusinessException.notFound("MISSION_ABSENTE"));
        if (!orgId.equals(m.getOrganisationId())) throw BusinessException.forbidden("MISSION_ORG_MISMATCH");
        return m;
    }

    private void apply(Mission m, MissionRequest req) {
        m.setTitre(req.titre());
        m.setDestination(req.destination());
        m.setPaysDestination(req.paysDestination());
        m.setObjectif(req.objectif());
        m.setDateDepart(req.dateDepart());
        m.setDateRetour(req.dateRetour());
        if (req.avanceDemandee() != null) m.setAvanceDemandee(req.avanceDemandee());
        if (req.avanceDevise() != null && !req.avanceDevise().isBlank()) m.setAvanceDevise(normalizeDevise(req.avanceDevise()));
    }

    private MissionResponse toResponseLight(Mission m) {
        List<FraisMission> frais = fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(m.getId());
        return toResponse(m, frais);
    }

    private MissionResponse toResponse(Mission m, List<FraisMission> frais) {
        int nbJours = (int) (m.getDateDepart() != null && m.getDateRetour() != null
                ? (m.getDateRetour().toEpochDay() - m.getDateDepart().toEpochDay() + 1)
                : 0);
        BigDecimal totalValides = BigDecimal.ZERO;
        for (FraisMission f : frais) {
            if (f.getStatut() == StatutFrais.VALIDE || f.getStatut() == StatutFrais.REMBOURSE) {
                totalValides = totalValides.add(f.getMontantEur());
            }
        }
        totalValides = totalValides.setScale(2, RoundingMode.HALF_UP);
        BigDecimal avance = m.getAvanceVersee() != null ? m.getAvanceVersee() : BigDecimal.ZERO;
        BigDecimal solde = totalValides.subtract(avance).setScale(2, RoundingMode.HALF_UP);

        String salNom = m.getSalarie() != null
                ? (m.getSalarie().getNom() + " " + m.getSalarie().getPrenom()).trim()
                : null;

        LocalDateTime created = m.getCreatedAt() != null
                ? LocalDateTime.ofInstant(m.getCreatedAt(), ZoneId.systemDefault())
                : null;

        List<FraisResponse> fraisResp = frais.stream().map(this::toFraisResponse).toList();

        String ordreUrl = (m.getOrdreMissionUrl() == null || m.getOrdreMissionUrl().isBlank())
                ? null
                : "/api/v1/missions/" + m.getId() + "/ordre-mission";

        return new MissionResponse(
                m.getId(),
                m.getTitre(),
                m.getDestination(),
                m.getPaysDestination(),
                m.getObjectif(),
                m.getDateDepart(),
                m.getDateRetour(),
                nbJours,
                m.getStatut() != null ? m.getStatut().name() : null,
                salNom,
                m.getAvanceDemandee(),
                m.getAvanceVersee(),
                totalValides,
                solde,
                ordreUrl,
                fraisResp,
                created
        );
    }

    private FraisResponse toFraisResponse(FraisMission f) {
        String just =
                (f.getJustificatifUrl() == null || f.getJustificatifUrl().isBlank())
                        ? null
                        : "/api/v1/missions/" + f.getMission().getId() + "/frais/" + f.getId() + "/justificatif";
        return new FraisResponse(
                f.getId(),
                f.getTypeFrais(),
                f.getDescription(),
                f.getDateFrais(),
                f.getMontant(),
                f.getDevise(),
                f.getMontantEur().setScale(2, RoundingMode.HALF_UP),
                just,
                f.getStatut() != null ? f.getStatut().name() : null
        );
    }

    @Transactional(readOnly = true)
    public String getOrdreObjectName(UUID missionId, UUID orgId) {
        Mission m = loadOwned(missionId, orgId);
        String obj = m.getOrdreMissionUrl();
        if (obj == null || obj.isBlank()) throw BusinessException.notFound("ORDRE_MISSION_ABSENT");
        String expectedPrefix = "missions/" + orgId + "/" + missionId + "/ordre/";
        if (!obj.startsWith(expectedPrefix)) throw BusinessException.forbidden("ORDRE_MISSION_ORG_MISMATCH");
        return obj;
    }

    @Transactional(readOnly = true)
    public String getRapportObjectName(UUID missionId, UUID orgId) {
        Mission m = loadOwned(missionId, orgId);
        String obj = m.getRapportUrl();
        if (obj == null || obj.isBlank()) throw BusinessException.notFound("RAPPORT_MISSION_ABSENT");
        String expectedPrefix = "missions/" + orgId + "/" + missionId + "/rapport/";
        if (!obj.startsWith(expectedPrefix)) throw BusinessException.forbidden("RAPPORT_MISSION_ORG_MISMATCH");
        return obj;
    }

    @Transactional(readOnly = true)
    public String getFraisJustificatifObjectName(UUID missionId, UUID fraisId, UUID orgId) {
        loadOwned(missionId, orgId);
        FraisMission f =
                fraisMissionRepository
                        .findByIdAndMission_Id(fraisId, missionId)
                        .orElseThrow(() -> BusinessException.notFound("FRAIS_ABSENT"));
        String obj = f.getJustificatifUrl();
        if (obj == null || obj.isBlank()) throw BusinessException.notFound("JUSTIFICATIF_ABSENT");
        String expectedPrefix = "missions/" + orgId + "/" + missionId + "/frais/";
        if (!obj.startsWith(expectedPrefix)) throw BusinessException.forbidden("JUSTIFICATIF_ORG_MISMATCH");
        return obj;
    }

    @Transactional(readOnly = true)
    public MinioStorageService.Download downloadObject(String objectName) throws Exception {
        return minioStorageService.download(objectName);
    }

    private String uploadPdf(String prefix, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("FICHIER_MANQUANT");
        if (file.getSize() > MAX_FILE) throw BusinessException.badRequest("FICHIER_TROP_GRAND");
        byte[] bytes = file.getBytes();
        String mime = new Tika().detect(bytes, file.getOriginalFilename());
        if (!"application/pdf".equals(mime)) throw BusinessException.badRequest("FICHIER_TYPE_INVALIDE");
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        String objectName = prefix + name.replaceAll("[^a-zA-Z0-9._-]", "_");
        minioStorageService.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, "application/pdf");
        return objectName;
    }

    private String uploadAny(String prefix, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("FICHIER_MANQUANT");
        if (file.getSize() > MAX_FILE) throw BusinessException.badRequest("FICHIER_TROP_GRAND");
        byte[] bytes = file.getBytes();
        String mime = new Tika().detect(bytes, file.getOriginalFilename());
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "justificatif";
        String objectName = prefix + name.replaceAll("[^a-zA-Z0-9._-]", "_");
        minioStorageService.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, mime != null ? mime : "application/octet-stream");
        return objectName;
    }

    private static StatutMission parseStatut(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return StatutMission.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    private static String fournisseurFor(Salarie s) {
        if (s == null) return "Mission";
        return (s.getNom() + " " + s.getPrenom()).trim();
    }

    private static String normalizeDevise(String d) {
        String x = d == null ? "EUR" : d.trim().toUpperCase(Locale.ROOT);
        return x.isBlank() ? "EUR" : x;
    }
}

