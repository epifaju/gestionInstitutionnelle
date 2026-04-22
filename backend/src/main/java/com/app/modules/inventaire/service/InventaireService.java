package com.app.modules.inventaire.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.inventaire.dto.BienRequest;
import com.app.modules.inventaire.dto.BienResponse;
import com.app.modules.inventaire.dto.MouvementResponse;
import com.app.modules.inventaire.dto.StatsInventaireResponse;
import com.app.modules.inventaire.entity.BienMateriel;
import com.app.modules.inventaire.entity.EtatBien;
import com.app.modules.inventaire.entity.MouvementBien;
import com.app.modules.inventaire.entity.TypeMouvementBien;
import com.app.modules.inventaire.repository.BienMaterielRepository;
import com.app.modules.inventaire.repository.BienMaterielSpecifications;
import com.app.modules.inventaire.repository.MouvementBienRepository;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventaireService {

    private final BienMaterielRepository bienRepo;
    private final MouvementBienRepository mouvementRepo;
    private final BienSequenceService bienSequenceService;
    private final SalarieRepository salarieRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<BienResponse> list(
            UUID orgId, String categorie, String etat, String localisation, Pageable pageable) {
        Specification<BienMateriel> spec = BienMaterielSpecifications.organisationIdEq(orgId);
        spec = BienMaterielSpecifications.and(spec, BienMaterielSpecifications.categorieContains(categorie));
        spec = BienMaterielSpecifications.and(spec, BienMaterielSpecifications.localisationContains(localisation));
        if (etat != null && !etat.isBlank()) {
            EtatBien e = parseEtat(etat);
            spec = BienMaterielSpecifications.and(spec, BienMaterielSpecifications.etatEq(e));
        }
        return bienRepo.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BienResponse getById(UUID id, UUID orgId) {
        BienMateriel b =
                bienRepo.findByIdAndOrganisationId(id, orgId).orElseThrow(() -> BusinessException.notFound("BIEN"));
        return toResponse(b);
    }

    @Transactional
    public BienResponse creer(BienRequest req, UUID orgId, UUID userId) {
        int annee =
                req.dateAcquisition() != null
                        ? req.dateAcquisition().getYear()
                        : LocalDate.now(ZoneId.systemDefault()).getYear();
        int seq = bienSequenceService.nextSequence(orgId, req.codeCategorie(), annee);
        String code = req.codeCategorie() + "-" + annee + "-" + String.format("%04d", seq);

        BienMateriel b = new BienMateriel();
        b.setOrganisationId(orgId);
        b.setCodeInventaire(code);
        b.setLibelle(req.libelle());
        b.setCategorie(req.categorie());
        b.setCodeCategorie(req.codeCategorie());
        b.setDateAcquisition(req.dateAcquisition());
        b.setValeurAchat(req.valeurAchat());
        b.setDevise(req.devise() != null && !req.devise().isBlank() ? req.devise() : "EUR");
        b.setLocalisation(req.localisation());
        b.setEtat(parseEtat(req.etat()));
        b.setResponsable(resolveResponsable(req.responsableId(), orgId));
        b.setDescription(req.description());

        b = bienRepo.save(b);

        MouvementBien m = new MouvementBien();
        m.setBien(b);
        m.setTypeMouvement(TypeMouvementBien.CREATION);
        m.setDateMouvement(Instant.now());
        m.setAuteur(utilisateurRepository.getReferenceById(userId));
        mouvementRepo.save(m);

        auditLogService.log(orgId, userId, "CREATE", "BienMateriel", b.getId(), null, snapshotBien(b));
        return toResponse(bienRepo.findByIdAndOrganisationId(b.getId(), orgId).orElse(b));
    }

    @Transactional
    public BienResponse modifier(UUID id, BienRequest req, UUID orgId, UUID userId) {
        BienMateriel b =
                bienRepo.findByIdAndOrganisationId(id, orgId).orElseThrow(() -> BusinessException.notFound("BIEN"));
        Map<String, Object> avantSnap = snapshotBien(b);
        Utilisateur auteur = utilisateurRepository.getReferenceById(userId);

        String oldLibelle = b.getLibelle();
        String oldCategorie = b.getCategorie();
        String oldCodeCat = b.getCodeCategorie();
        LocalDate oldDate = b.getDateAcquisition();
        BigDecimal oldVal = b.getValeurAchat();
        String oldDev = b.getDevise();
        String oldLoc = b.getLocalisation();
        EtatBien oldEtat = b.getEtat();
        UUID oldResp = b.getResponsable() != null ? b.getResponsable().getId() : null;
        String oldDesc = b.getDescription();

        Salarie newResp = resolveResponsable(req.responsableId(), orgId);
        UUID newRespId = newResp != null ? newResp.getId() : null;
        EtatBien newEtat = parseEtat(req.etat());

        recordChange(b, auteur, "libelle", oldLibelle, req.libelle(), movementTypeForField("libelle"));
        recordChange(b, auteur, "categorie", oldCategorie, req.categorie(), movementTypeForField("categorie"));
        recordChange(b, auteur, "codeCategorie", oldCodeCat, req.codeCategorie(), movementTypeForField("codeCategorie"));
        recordChange(
                b,
                auteur,
                "dateAcquisition",
                oldDate != null ? oldDate.toString() : null,
                req.dateAcquisition() != null ? req.dateAcquisition().toString() : null,
                movementTypeForField("dateAcquisition"));
        if (oldVal == null && req.valeurAchat() != null
                || oldVal != null && req.valeurAchat() == null
                || oldVal != null && req.valeurAchat() != null && oldVal.compareTo(req.valeurAchat()) != 0) {
            recordChange(
                    b,
                    auteur,
                    "valeurAchat",
                    oldVal != null ? oldVal.toPlainString() : null,
                    req.valeurAchat() != null ? req.valeurAchat().toPlainString() : null,
                    movementTypeForField("valeurAchat"));
        }
        recordChange(b, auteur, "devise", oldDev, req.devise(), movementTypeForField("devise"));
        recordChange(b, auteur, "localisation", oldLoc, req.localisation(), TypeMouvementBien.DEPLACEMENT);
        recordChange(
                b,
                auteur,
                "etat",
                oldEtat != null ? oldEtat.name() : null,
                newEtat.name(),
                TypeMouvementBien.ETAT);
        recordChange(
                b,
                auteur,
                "responsableId",
                oldResp != null ? oldResp.toString() : null,
                newRespId != null ? newRespId.toString() : null,
                TypeMouvementBien.AFFECTATION);
        recordChange(b, auteur, "description", oldDesc, req.description(), movementTypeForField("description"));

        b.setLibelle(req.libelle());
        b.setCategorie(req.categorie());
        b.setCodeCategorie(req.codeCategorie());
        b.setDateAcquisition(req.dateAcquisition());
        b.setValeurAchat(req.valeurAchat());
        if (req.devise() != null && !req.devise().isBlank()) {
            b.setDevise(req.devise());
        }
        b.setLocalisation(req.localisation());
        b.setEtat(newEtat);
        b.setResponsable(newResp);
        b.setDescription(req.description());

        bienRepo.save(b);
        BienMateriel refreshed = bienRepo.findByIdAndOrganisationId(id, orgId).orElse(b);
        auditLogService.log(orgId, userId, "UPDATE", "BienMateriel", id, avantSnap, snapshotBien(refreshed));
        return toResponse(refreshed);
    }

    private void recordChange(
            BienMateriel bien,
            Utilisateur auteur,
            String champ,
            String ancien,
            String nouveau,
            TypeMouvementBien type) {
        if (Objects.equals(ancien, nouveau)) {
            return;
        }
        MouvementBien m = new MouvementBien();
        m.setBien(bien);
        m.setTypeMouvement(type);
        m.setChampModifie(champ);
        m.setAncienneValeur(ancien);
        m.setNouvelleValeur(nouveau);
        m.setDateMouvement(Instant.now());
        m.setAuteur(auteur);
        mouvementRepo.save(m);
    }

    private TypeMouvementBien movementTypeForField(String champ) {
        return switch (champ) {
            case "responsableId" -> TypeMouvementBien.AFFECTATION;
            case "localisation" -> TypeMouvementBien.DEPLACEMENT;
            case "etat" -> TypeMouvementBien.ETAT;
            default -> TypeMouvementBien.DEPLACEMENT;
        };
    }

    @Transactional
    public void reformer(UUID id, String motif, UUID orgId, UUID userId) {
        if (motif == null || motif.isBlank()) {
            throw BusinessException.badRequest("MOTIF_REFORME_REQUIS");
        }
        BienMateriel b =
                bienRepo.findByIdAndOrganisationId(id, orgId).orElseThrow(() -> BusinessException.notFound("BIEN"));
        Utilisateur auteur = utilisateurRepository.getReferenceById(userId);
        String oldEtat = b.getEtat() != null ? b.getEtat().name() : null;
        b.setEtat(EtatBien.HORS_SERVICE);
        bienRepo.save(b);

        MouvementBien m = new MouvementBien();
        m.setBien(b);
        m.setTypeMouvement(TypeMouvementBien.REFORME);
        m.setChampModifie("etat");
        m.setAncienneValeur(oldEtat);
        m.setNouvelleValeur(EtatBien.HORS_SERVICE.name());
        m.setMotif(motif);
        m.setDateMouvement(Instant.now());
        m.setAuteur(auteur);
        mouvementRepo.save(m);
        Map<String, Object> avant = new LinkedHashMap<>();
        avant.put("etat", oldEtat);
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("etat", EtatBien.HORS_SERVICE.name());
        apres.put("motif", motif);
        auditLogService.log(orgId, userId, "UPDATE", "BienMateriel", id, avant, apres);
    }

    @Transactional(readOnly = true)
    public List<MouvementResponse> getHistorique(UUID id, UUID orgId) {
        bienRepo.findByIdAndOrganisationId(id, orgId).orElseThrow(() -> BusinessException.notFound("BIEN"));
        List<MouvementBien> list = mouvementRepo.findByBien_IdOrderByDateMouvementDesc(id);
        return list.stream().map(this::toMouvementResponse).toList();
    }

    @Transactional(readOnly = true)
    public StatsInventaireResponse getStats(UUID orgId) {
        BigDecimal valeur =
                jdbcTemplate.query(
                        """
                                SELECT COALESCE(SUM(valeur_achat), 0) FROM biens_materiels
                                WHERE organisation_id = ? AND etat <> 'HORS_SERVICE'
                                """,
                        (ResultSetExtractor<BigDecimal>)
                                rs -> {
                                    if (rs.next()) {
                                        return rs.getBigDecimal(1);
                                    }
                                    return BigDecimal.ZERO;
                                },
                        orgId);

        List<StatsInventaireResponse.CompteParEtat> parEtat =
                jdbcTemplate.query(
                        """
                                SELECT etat::text, COUNT(*) FROM biens_materiels
                                WHERE organisation_id = ?
                                GROUP BY etat
                                ORDER BY etat
                                """,
                        (rs, row) ->
                                new StatsInventaireResponse.CompteParEtat(rs.getString(1), rs.getLong(2)),
                        orgId);

        List<StatsInventaireResponse.CompteParCategorie> parCat =
                jdbcTemplate.query(
                        """
                                SELECT categorie, COUNT(*) FROM biens_materiels
                                WHERE organisation_id = ?
                                GROUP BY categorie
                                ORDER BY categorie
                                """,
                        (rs, row) ->
                                new StatsInventaireResponse.CompteParCategorie(rs.getString(1), rs.getLong(2)),
                        orgId);

        return new StatsInventaireResponse(valeur, parEtat, parCat);
    }

    private Map<String, Object> snapshotBien(BienMateriel b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codeInventaire", b.getCodeInventaire());
        m.put("libelle", b.getLibelle());
        m.put("categorie", b.getCategorie());
        m.put("etat", b.getEtat() != null ? b.getEtat().name() : null);
        m.put("valeurAchat", b.getValeurAchat());
        m.put("devise", b.getDevise());
        m.put("localisation", b.getLocalisation());
        return m;
    }

    private BienResponse toResponse(BienMateriel b) {
        String respNom = null;
        if (b.getResponsable() != null) {
            Salarie s = b.getResponsable();
            respNom = s.getPrenom() + " " + s.getNom();
        }
        return new BienResponse(
                b.getId(),
                b.getCodeInventaire(),
                b.getLibelle(),
                b.getCategorie(),
                b.getCodeCategorie(),
                b.getDateAcquisition(),
                b.getValeurAchat(),
                b.getDevise(),
                b.getLocalisation(),
                b.getEtat() != null ? b.getEtat().name() : null,
                respNom,
                b.getCreatedAt() != null ? toLocalDateTime(b.getCreatedAt()) : null,
                b.getUpdatedAt() != null ? toLocalDateTime(b.getUpdatedAt()) : null);
    }

    private MouvementResponse toMouvementResponse(MouvementBien m) {
        String auteurNom = null;
        if (m.getAuteur() != null) {
            Utilisateur u = m.getAuteur();
            auteurNom = (u.getPrenom() != null ? u.getPrenom() + " " : "") + (u.getNom() != null ? u.getNom() : "");
            auteurNom = auteurNom.trim();
        }
        return new MouvementResponse(
                m.getId(),
                m.getTypeMouvement() != null ? m.getTypeMouvement().name() : null,
                m.getChampModifie(),
                m.getAncienneValeur(),
                m.getNouvelleValeur(),
                m.getMotif(),
                auteurNom,
                m.getDateMouvement() != null ? toLocalDateTime(m.getDateMouvement()) : null);
    }

    private Salarie resolveResponsable(UUID responsableId, UUID orgId) {
        if (responsableId == null) {
            return null;
        }
        Salarie s =
                salarieRepository
                        .findById(responsableId)
                        .filter(x -> orgId.equals(x.getOrganisationId()))
                        .orElseThrow(() -> BusinessException.badRequest("RESPONSABLE_INVALIDE"));
        return s;
    }

    private static EtatBien parseEtat(String raw) {
        try {
            return EtatBien.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw BusinessException.badRequest("ETAT_BIEN_INVALIDE");
        }
    }

    private static LocalDateTime toLocalDateTime(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneId.systemDefault());
    }
}
