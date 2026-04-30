package com.app.modules.rapports.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.modules.missions.entity.FraisMission;
import com.app.modules.missions.entity.Mission;
import com.app.modules.missions.entity.StatutFrais;
import com.app.modules.missions.repository.FraisMissionRepository;
import com.app.modules.missions.repository.MissionRepository;
import com.app.modules.rapports.dto.ExportJobResponse;
import com.app.modules.rapports.entity.TypeExport;
import com.app.shared.exception.BusinessException;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportNotefraisService {

    private static final int MAX_SYNC_LINES = 500;

    private final PdfBuilderService pdfBuilderService;
    private final MissionRepository missionRepository;
    private final FraisMissionRepository fraisMissionRepository;
    private final ExportMinioService exportMinioService;
    private final ExportJobService exportJobService;
    private final AuditLogService auditLogService;
    private final UtilisateurRepository utilisateurRepository;

    @Transactional(readOnly = true)
    public ExportJobResponse exporterNoteFrais(UUID missionId, UUID orgId, UUID userId) {
        Mission mission =
                missionRepository
                        .findById(missionId)
                        .orElseThrow(() -> BusinessException.notFound("MISSION_NOT_FOUND"));
        if (!orgId.equals(mission.getOrganisationId())) {
            throw BusinessException.notFound("MISSION_NOT_FOUND");
        }

        List<FraisMission> frais = fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(missionId);
        int nb = frais != null ? frais.size() : 0;
        if (nb == 0) {
            throw BusinessException.badRequest("EXPORT_DONNEES_ABSENTES");
        }

        if (nb <= MAX_SYNC_LINES) {
            return generateSync(mission, frais, orgId, userId);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("missionId", missionId);
        ExportJobResponse job = exportJobService.creerJob(orgId, userId, TypeExport.NOTE_FRAIS_PDF, params);
        return job;
    }

    @Async("exportExecutor")
    public void generateAsync(UUID jobId, UUID missionId, UUID orgId, UUID userId) {
        try {
            exportJobService.mettreAJourProgression(jobId, 10);
            Mission mission =
                    missionRepository
                            .findById(missionId)
                            .orElseThrow(() -> BusinessException.notFound("MISSION_NOT_FOUND"));
            if (!orgId.equals(mission.getOrganisationId())) throw BusinessException.notFound("MISSION_NOT_FOUND");
            List<FraisMission> frais = fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(missionId);

            byte[] pdfBytes = buildPdfBytes(mission, frais, orgId);
            String objectName =
                    orgId
                            + "/exports/notes-frais/"
                            + mission.getId()
                            + "/"
                            + System.currentTimeMillis()
                            + ".pdf";
            exportMinioService.uploadBytes(objectName, pdfBytes, "application/pdf");
            String url = exportMinioService.presignGet(objectName, 48 * 3600);
            exportJobService.setObjectName(jobId, objectName);

            Map<String, Object> apres = Map.of("type", TypeExport.NOTE_FRAIS_PDF.name(), "genere_par", userId);
            auditLogService.log(orgId, userId, "EXPORT", "NoteFrais", mission.getId(), null, apres);

            exportJobService.marquerTermine(jobId, url, "note-frais-" + mission.getId() + ".pdf", pdfBytes.length, frais.size());
        } catch (Exception e) {
            log.error("Async NOTE_FRAIS_PDF failed jobId={}", jobId, e);
            exportJobService.marquerErreur(jobId, e.getMessage());
        }
    }

    @Transactional
    protected ExportJobResponse generateSync(Mission mission, List<FraisMission> frais, UUID orgId, UUID userId) {
        byte[] pdfBytes = buildPdfBytes(mission, frais, orgId);

        String objectName =
                orgId
                        + "/exports/notes-frais/"
                        + mission.getId()
                        + "/"
                        + System.currentTimeMillis()
                        + ".pdf";
        try {
            exportMinioService.uploadBytes(objectName, pdfBytes, "application/pdf");
            String url = exportMinioService.presignGet(objectName, 48 * 3600);
            String nomFichier = "note-frais-" + mission.getId() + ".pdf";

            Map<String, Object> apres = Map.of("type", TypeExport.NOTE_FRAIS_PDF.name(), "genere_par", userId);
            auditLogService.log(orgId, userId, "EXPORT", "NoteFrais", mission.getId(), null, apres);

            ExportJobResponse r =
                    new ExportJobResponse(
                            null,
                            TypeExport.NOTE_FRAIS_PDF.name(),
                            "TERMINE",
                            100,
                            url,
                            nomFichier,
                            (long) pdfBytes.length,
                            frais.size(),
                            null,
                            null,
                            java.time.LocalDateTime.now());
            return r;
        } catch (Exception e) {
            throw new BusinessException("EXPORT_MINIO_ERROR", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Erreur lors de l'upload MinIO.");
        }
    }

    private byte[] buildPdfBytes(Mission mission, List<FraisMission> frais, UUID orgId) {
        frais = frais.stream().sorted(Comparator.comparing(FraisMission::getDateFrais)).toList();

        PdfDocument pdfDoc = pdfBuilderService.creerDocument(orgId, "NOTE DE FRAIS");
        Document doc = new Document(pdfDoc);
        doc.setMargins(80, 30, 40, 30);

        pdfBuilderService.ajouterSection(doc, "Informations générales");
        Table info = new Table(new float[] {35, 65}).useAllAvailableWidth();
        info.addCell(labelCell("Mission"));
        info.addCell(valueCell(mission.getTitre()));
        info.addCell(labelCell("Destination"));
        info.addCell(valueCell(mission.getDestination() + (mission.getPaysDestination() != null ? " (" + mission.getPaysDestination() + ")" : "")));
        info.addCell(labelCell("Dates"));
        info.addCell(valueCell(mission.getDateDepart() + " → " + mission.getDateRetour()));
        info.addCell(labelCell("Salarié"));
        var s = mission.getSalarie();
        String salarie =
                (s != null ? (s.getPrenom() + " " + s.getNom()) : "")
                        + (s != null ? " — " + s.getMatricule() + " — " + s.getService() : "");
        info.addCell(valueCell(salarie));
        info.addCell(labelCell("Statut"));
        info.addCell(valueCell(mission.getStatut() != null ? mission.getStatut().name() : ""));
        info.addCell(labelCell("Avance versée"));
        info.addCell(valueCell(fmtMoney(mission.getAvanceVersee()) + " " + nz(mission.getAvanceDevise(), "EUR")));

        BigDecimal totalEur =
                frais.stream()
                        .filter(f -> f.getStatut() == StatutFrais.VALIDE || f.getStatut() == StatutFrais.REMBOURSE)
                        .map(FraisMission::getMontantEur)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal solde = totalEur.subtract(mission.getAvanceVersee() != null ? mission.getAvanceVersee() : BigDecimal.ZERO);

        info.addCell(labelCell("Total frais (EUR)"));
        info.addCell(valueCell(fmtMoney(totalEur) + " EUR"));
        info.addCell(labelCell("Solde à régler (EUR)"));
        info.addCell(valueCell(fmtMoney(solde) + " EUR"));

        String approbateur = "";
        if (mission.getApprobateurId() != null) {
            approbateur =
                    utilisateurRepository
                            .findById(mission.getApprobateurId())
                            .map(u -> (u.getPrenom() != null ? u.getPrenom() : "") + " " + (u.getNom() != null ? u.getNom() : ""))
                            .orElse(mission.getApprobateurId().toString());
            if (mission.getDateApprobation() != null) {
                approbateur +=
                        " — "
                                + DateTimeFormatter.ofPattern("dd/MM/yyyy")
                                        .format(mission.getDateApprobation().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
            }
        }
        info.addCell(labelCell("Approbateur"));
        info.addCell(valueCell(approbateur));
        doc.add(info);

        pdfBuilderService.ajouterSection(doc, "Détail des frais");
        Table t =
                pdfBuilderService.creerTableau(
                        new String[] {"Date", "Type", "Description", "Montant", "Devise", "Taux EUR", "Montant EUR", "Statut", "Justificatif"},
                        new float[] {10, 12, 22, 10, 7, 8, 11, 10, 10});
        for (FraisMission f : frais) {
            t.addCell(cell(f.getDateFrais() != null ? f.getDateFrais().toString() : ""));
            t.addCell(cell(nz(f.getTypeFrais(), "")));
            t.addCell(cell(nz(f.getDescription(), "")));
            t.addCell(cell(fmtMoney(f.getMontant())).setTextAlignment(TextAlignment.RIGHT));
            t.addCell(cell(nz(f.getDevise(), "")));
            t.addCell(cell(f.getTauxChangeEur() != null ? f.getTauxChangeEur().toPlainString() : ""));
            t.addCell(cell(fmtMoney(f.getMontantEur())).setTextAlignment(TextAlignment.RIGHT));
            t.addCell(cell(f.getStatut() != null ? f.getStatut().name() : ""));
            t.addCell(cell(f.getJustificatifUrl() != null && !f.getJustificatifUrl().isBlank() ? "Oui" : "—"));
        }
        doc.add(t);

        pdfBuilderService.ajouterSection(doc, "Pièces justificatives");
        int idx = 1;
        for (FraisMission f : frais) {
            if (f.getJustificatifUrl() == null || f.getJustificatifUrl().isBlank()) continue;
            String lib = idx + ". " + nz(f.getTypeFrais(), "Justificatif") + " — " + nz(f.getDescription(), "");
            pdfBuilderService.ajouterPieceJointe(doc, lib, f.getJustificatifUrl());
            idx++;
        }
        if (idx == 1) {
            doc.add(new Paragraph("Aucune pièce justificative.").setItalic());
        }

        pdfBuilderService.ajouterSection(doc, "Signatures");
        Table sig = new Table(new float[] {33, 33, 34}).useAllAvailableWidth();
        sig.addCell(sigCell("Établi par\n\n\n"));
        sig.addCell(sigCell("Validé par\n\n\n"));
        sig.addCell(sigCell("Visa RH\n\n\n"));
        doc.add(sig);

        return pdfBuilderService.finaliser(pdfDoc, doc);
    }

    private static Cell labelCell(String s) {
        return new Cell().add(new Paragraph(nz(s, "")).setBold()).setPadding(4);
    }

    private static Cell valueCell(String s) {
        return new Cell().add(new Paragraph(nz(s, ""))).setPadding(4);
    }

    private static Cell sigCell(String s) {
        return new Cell().add(new Paragraph(nz(s, ""))).setPadding(8).setMinHeight(80);
    }

    private static Cell cell(String s) {
        return new Cell().add(new Paragraph(nz(s, ""))).setPadding(4);
    }

    private static String nz(String s, String def) {
        return s == null ? def : s;
    }

    private static String fmtMoney(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}

