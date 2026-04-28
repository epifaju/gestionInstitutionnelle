package com.app.modules.rh.service;

import com.app.modules.rh.dto.ContratRequest;
import com.app.modules.rh.dto.ContratResponse;
import com.app.modules.rh.dto.DecisionFinCddRequest;
import com.app.modules.rh.dto.RenouvellementCddRequest;
import com.app.modules.rh.entity.ContratSalarie;
import com.app.modules.rh.entity.DecisionFinCdd;
import com.app.modules.rh.entity.EcheanceRh;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutEcheance;
import com.app.modules.rh.entity.TypeEcheance;
import com.app.modules.rh.repository.ContratSalarieRepository;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.modules.rh.repository.ConfigAlerteRhRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
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
public class ContratService {

    private static final long MAX_FILE = 10_485_760L;

    private final ContratSalarieRepository contratRepository;
    private final EcheanceRhRepository echeanceRepository;
    private final SalarieRepository salarieRepository;
    private final ConfigAlerteRhRepository configRepository;
    private final MinioStorageService minioStorageService;

    public ContratResponse creerContrat(ContratRequest req, UUID salarieId, UUID orgId, UUID userId) {
        Salarie s = salarieRepository.findByIdAndOrganisationId(salarieId, orgId)
                .orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));

        String type = normalizeType(req.typeContrat());
        if ("CDD".equals(type)) {
            if (req.motifCdd() == null || req.motifCdd().isBlank()) {
                throw BusinessException.badRequest("DOCUMENT_REQUIS");
            }
        }

        if (req.dateFinContrat() != null && !req.dateFinContrat().isAfter(req.dateDebutContrat())) {
            throw BusinessException.badRequest("ECHEANCE_DATE_INVALIDE");
        }

        // désactive ancien contrat actif
        contratRepository.findBySalarieIdAndActifTrue(salarieId).ifPresent(old -> {
            old.setActif(false);
            contratRepository.save(old);
        });

        ContratSalarie c = new ContratSalarie();
        c.setOrganisationId(orgId);
        c.setSalarie(s);
        c.setTypeContrat(type);
        c.setDateDebutContrat(req.dateDebutContrat());
        c.setDateFinContrat(req.dateFinContrat());
        c.setDateFinPeriodeEssai(req.dateFinPeriodeEssai());
        c.setDureeEssaiMois(req.dureeEssaiMois());
        c.setNumeroContrat(req.numeroContrat());
        c.setIntitulePoste(req.intitulePoste());
        c.setMotifCdd(req.motifCdd());
        c.setConventionCollective(req.conventionCollective());
        c.setRenouvellementNumero(0);
        c.setContratParentId(null);
        c.setDecisionFin(DecisionFinCdd.EN_ATTENTE);
        c.setDateDecision(null);
        c.setCommentaireDecision(null);
        c.setContratSigneUrl(null);
        c.setAvenantUrl(null);
        c.setActif(true);
        c.setCreatedBy(userId);
        c = contratRepository.save(c);

        // échéances auto
        if (req.dateFinContrat() != null) {
            createEcheanceAuto(orgId, userId, s, c.getId(), TypeEcheance.FIN_CDD,
                    "Fin de contrat CDD — " + nomComplet(s),
                    req.dateFinContrat());
        }
        if (req.dateFinPeriodeEssai() != null) {
            createEcheanceAuto(orgId, userId, s, c.getId(), TypeEcheance.FIN_PERIODE_ESSAI,
                    "Fin de période d'essai — " + nomComplet(s),
                    req.dateFinPeriodeEssai());
        }

        return toResponse(c);
    }

    public ContratResponse renouvelerCdd(UUID contratId, RenouvellementCddRequest req, UUID orgId, UUID userId) {
        ContratSalarie ancien = contratRepository.findByIdAndOrganisationId(contratId, orgId)
                .orElseThrow(() -> BusinessException.notFound("CONTRAT_ABSENT"));

        int max = configRepository.findByOrganisationId(orgId).map(x -> x.getMaxRenouvellementsCdd()).orElse(2);
        if (ancien.getRenouvellementNumero() != null && ancien.getRenouvellementNumero() >= max) {
            throw BusinessException.badRequest("ECHEANCE_RENOUVELLEMENT_IMPOSSIBLE");
        }
        if (ancien.getDecisionFin() == DecisionFinCdd.NON_RENOUVELE || ancien.getDecisionFin() == DecisionFinCdd.CDI) {
            throw BusinessException.badRequest("ECHEANCE_RENOUVELLEMENT_IMPOSSIBLE");
        }
        if (req.nouvelleDateFin() == null || (ancien.getDateDebutContrat() != null && !req.nouvelleDateFin().isAfter(ancien.getDateDebutContrat()))) {
            throw BusinessException.badRequest("ECHEANCE_DATE_INVALIDE");
        }

        ancien.setActif(false);
        ancien.setDecisionFin(DecisionFinCdd.RENOUVELLEMENT);
        ancien.setDateDecision(LocalDate.now());
        if (req.commentaire() != null && !req.commentaire().isBlank()) {
            ancien.setCommentaireDecision(req.commentaire());
        }
        contratRepository.save(ancien);

        ContratSalarie neu = new ContratSalarie();
        neu.setOrganisationId(orgId);
        neu.setSalarie(ancien.getSalarie());
        neu.setTypeContrat("CDD");
        neu.setDateDebutContrat(ancien.getDateDebutContrat());
        neu.setDateFinContrat(req.nouvelleDateFin());
        neu.setDateFinPeriodeEssai(ancien.getDateFinPeriodeEssai());
        neu.setDureeEssaiMois(ancien.getDureeEssaiMois());
        neu.setNumeroContrat(ancien.getNumeroContrat());
        neu.setIntitulePoste(ancien.getIntitulePoste());
        neu.setMotifCdd(req.motif());
        neu.setConventionCollective(ancien.getConventionCollective());
        neu.setRenouvellementNumero((ancien.getRenouvellementNumero() == null ? 0 : ancien.getRenouvellementNumero()) + 1);
        neu.setContratParentId(ancien.getId());
        neu.setDecisionFin(DecisionFinCdd.EN_ATTENTE);
        neu.setCreatedBy(userId);
        neu.setActif(true);
        neu = contratRepository.save(neu);

        // annule l'ancienne échéance FIN_CDD (liée à l'ancien contrat)
        echeanceRepository.findByOrganisationIdAndContratIdAndType(orgId, ancien.getId(), TypeEcheance.FIN_CDD)
                .ifPresent(e -> {
                    e.setStatut(StatutEcheance.ANNULEE);
                    echeanceRepository.save(e);
                });

        createEcheanceAuto(orgId, userId, ancien.getSalarie(), neu.getId(), TypeEcheance.FIN_CDD,
                "Fin de contrat CDD — " + nomComplet(ancien.getSalarie()),
                req.nouvelleDateFin());

        return toResponse(neu);
    }

    public ContratResponse enregistrerDecisionFin(UUID contratId, DecisionFinCddRequest req, UUID orgId) {
        ContratSalarie c = contratRepository.findByIdAndOrganisationId(contratId, orgId)
                .orElseThrow(() -> BusinessException.notFound("CONTRAT_ABSENT"));

        DecisionFinCdd decision;
        try {
            decision = DecisionFinCdd.valueOf(req.decision().trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw BusinessException.badRequest("CONTRAT_STATUT_INVALIDE");
        }

        c.setDecisionFin(decision);
        c.setDateDecision(req.dateDecision() != null ? req.dateDecision() : LocalDate.now());
        c.setCommentaireDecision(req.commentaire());

        if (decision == DecisionFinCdd.CDI) {
            // bascule salarié en CDI et créer un contrat CDI actif
            Salarie s = c.getSalarie();
            s.setTypeContrat("CDI");
            salarieRepository.save(s);

            c.setActif(false);
            c.setDateFinContrat(null);
            contratRepository.save(c);

            ContratSalarie cdi = new ContratSalarie();
            cdi.setOrganisationId(orgId);
            cdi.setSalarie(s);
            cdi.setTypeContrat("CDI");
            cdi.setDateDebutContrat(LocalDate.now());
            cdi.setDateFinContrat(null);
            cdi.setDateFinPeriodeEssai(null);
            cdi.setDureeEssaiMois(null);
            cdi.setNumeroContrat(null);
            cdi.setIntitulePoste(c.getIntitulePoste());
            cdi.setMotifCdd(null);
            cdi.setConventionCollective(c.getConventionCollective());
            cdi.setRenouvellementNumero(0);
            cdi.setContratParentId(c.getId());
            cdi.setDecisionFin(DecisionFinCdd.EN_ATTENTE);
            cdi.setActif(true);
            cdi = contratRepository.save(cdi);
            return toResponse(cdi);
        }

        if (decision == DecisionFinCdd.NON_RENOUVELE) {
            echeanceRepository.findByOrganisationIdAndContratIdAndType(orgId, c.getId(), TypeEcheance.FIN_CDD)
                    .ifPresent(e -> {
                        e.setStatut(StatutEcheance.ACTION_REQUISE);
                        echeanceRepository.save(e);
                    });
        }

        contratRepository.save(c);
        return toResponse(c);
    }

    public String uploadContratSigne(UUID contratId, MultipartFile file, UUID orgId) {
        ContratSalarie c = contratRepository.findByIdAndOrganisationId(contratId, orgId)
                .orElseThrow(() -> BusinessException.notFound("CONTRAT_ABSENT"));

        String objectName = uploadPdf(orgId + "/contrats/" + c.getSalarie().getId() + "/" + c.getId() + "/", "contrat-signe.pdf", file);
        c.setContratSigneUrl(objectName);
        contratRepository.save(c);
        try {
            return minioStorageService.presignedGetUrl(objectName);
        } catch (Exception e) {
            return objectName;
        }
    }

    @Transactional(readOnly = true)
    public ContratResponse getContratActif(UUID salarieId, UUID orgId) {
        ContratSalarie c = contratRepository.findBySalarieIdAndActifTrue(salarieId)
                .filter(x -> orgId.equals(x.getOrganisationId()))
                .orElseThrow(() -> BusinessException.notFound("CONTRAT_ABSENT"));
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public Page<ContratResponse> listContrats(UUID orgId, String typeContrat, String service, Pageable p) {
        String t = typeContrat == null || typeContrat.isBlank() ? null : normalizeType(typeContrat);
        String svc = service == null || service.isBlank() ? null : service.trim();
        return contratRepository.listActifs(orgId, t, svc, p).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ContratResponse> getCddExpirantDans(UUID orgId, int nbJours) {
        LocalDate today = LocalDate.now();
        LocalDate max = today.plusDays(Math.max(0, nbJours));
        return contratRepository.findCddExpirantDans(orgId, today, max).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ContratResponse> historiqueContrats(UUID salarieId, UUID orgId) {
        salarieRepository.findByIdAndOrganisationId(salarieId, orgId).orElseThrow(() -> BusinessException.notFound("SALARIE_NON_TROUVE"));
        return contratRepository.findBySalarieIdOrderByCreatedAtDesc(salarieId).stream()
                .filter(c -> orgId.equals(c.getOrganisationId()))
                .map(this::toResponse)
                .toList();
    }

    private void createEcheanceAuto(UUID orgId, UUID userId, Salarie s, UUID contratId, TypeEcheance type, String titre, LocalDate dateEcheance) {
        if (dateEcheance == null) return;
        List<StatutEcheance> closed = List.of(StatutEcheance.TRAITEE, StatutEcheance.ANNULEE, StatutEcheance.EXPIREE);
        boolean exists = echeanceRepository.existsByOrganisationIdAndSalarie_IdAndTypeEcheanceAndDateEcheanceAndStatutNotIn(
                orgId, s.getId(), type, dateEcheance, closed
        );
        if (exists) {
            throw BusinessException.badRequest("ECHEANCE_DEJA_EXISTANTE");
        }
        EcheanceRh e = new EcheanceRh();
        e.setOrganisationId(orgId);
        e.setSalarie(s);
        e.setContratId(contratId);
        e.setTypeEcheance(type);
        e.setTitre(titre);
        e.setDescription(null);
        e.setDateEcheance(dateEcheance);
        e.setPriorite(2);
        e.setResponsableId(null);
        e.setCreatedBy(userId);

        StatutEcheance initial = calcStatutInitial(dateEcheance, LocalDate.now());
        e.setStatut(initial);
        echeanceRepository.save(e);
    }

    private static StatutEcheance calcStatutInitial(LocalDate dateEcheance, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, dateEcheance);
        if (days <= 7) return StatutEcheance.ACTION_REQUISE;
        if (days <= 30) return StatutEcheance.EN_ALERTE;
        return StatutEcheance.A_VENIR;
    }

    private ContratResponse toResponse(ContratSalarie c) {
        Salarie s = c.getSalarie();
        String nom = nomComplet(s);
        String matricule = s.getMatricule();

        LocalDate today = LocalDate.now();
        Integer joursAvantFin = null;
        String niveauUrgence = "NORMAL";
        if (c.getDateFinContrat() != null) {
            long d = ChronoUnit.DAYS.between(today, c.getDateFinContrat());
            joursAvantFin = (int) d;
            niveauUrgence = urgenceFromDays(d);
            if (d < 0) niveauUrgence = "CRITIQUE";
        }

        LocalDateTime created = c.getCreatedAt() != null
                ? LocalDateTime.ofInstant(c.getCreatedAt(), ZoneId.systemDefault())
                : null;

        String signedUrl = presignMaybe(c.getContratSigneUrl());

        return new ContratResponse(
                c.getId(),
                s.getId(),
                nom,
                matricule,
                s.getService(),
                c.getTypeContrat(),
                c.getDateDebutContrat(),
                c.getDateFinContrat(),
                c.getDateFinPeriodeEssai(),
                c.getDureeEssaiMois(),
                c.getNumeroContrat(),
                c.getIntitulePoste(),
                c.getMotifCdd(),
                c.getConventionCollective(),
                c.getRenouvellementNumero(),
                c.getContratParentId(),
                c.getDecisionFin() != null ? c.getDecisionFin().name() : null,
                c.getDateDecision(),
                c.getCommentaireDecision(),
                signedUrl,
                c.isActif(),
                joursAvantFin != null && joursAvantFin >= 0 ? joursAvantFin : (c.getDateFinContrat() == null ? null : joursAvantFin),
                niveauUrgence,
                created
        );
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

    private static String urgenceFromDays(long days) {
        if (days < 0) return "CRITIQUE";
        if (days <= 7) return "URGENT";
        if (days <= 30) return "ATTENTION";
        return "NORMAL";
    }

    private static String nomComplet(Salarie s) {
        if (s == null) return null;
        String a = s.getNom() == null ? "" : s.getNom().trim();
        String b = s.getPrenom() == null ? "" : s.getPrenom().trim();
        String out = (a + " " + b).trim();
        return out.isBlank() ? null : out;
    }

    private static String normalizeType(String x) {
        return x == null ? "" : x.trim().toUpperCase(Locale.ROOT);
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
}

