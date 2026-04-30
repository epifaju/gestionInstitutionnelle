package com.app.modules.rapports.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rapports.dto.ConfigExportRequest;
import com.app.modules.rapports.dto.ExportBudgetRequest;
import com.app.modules.rapports.dto.ExportEtatPaieRequest;
import com.app.modules.rapports.dto.ExportJobResponse;
import com.app.modules.rapports.dto.ExportJournalAuditRequest;
import com.app.modules.rapports.dto.ExportNotefraisRequest;
import com.app.modules.rapports.entity.ConfigExport;
import com.app.modules.rapports.entity.TypeExport;
import com.app.modules.rapports.repository.ConfigExportRepository;
import com.app.modules.rapports.service.ExportBudgetService;
import com.app.modules.rapports.service.ExportEtatPaieService;
import com.app.modules.rapports.service.ExportJobService;
import com.app.modules.rapports.service.ExportJournalAuditService;
import com.app.modules.rapports.service.ExportMinioService;
import com.app.modules.rapports.service.ExportNotefraisService;
import com.app.shared.dto.ApiResponse;
import com.app.shared.dto.PageResponse;
import com.app.shared.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
public class ExportConformiteController {

    private static final int MAX_LOGO_BYTES = 2 * 1024 * 1024;

    private final ExportNotefraisService exportNotefraisService;
    private final ExportEtatPaieService exportEtatPaieService;
    private final ExportBudgetService exportBudgetService;
    private final ExportJournalAuditService exportJournalAuditService;
    private final ExportJobService exportJobService;
    private final ConfigExportRepository configExportRepository;
    private final ExportMinioService exportMinioService;
    private final com.app.audit.AuditLogRepository auditLogRepository;

    // ── NOTE DE FRAIS ──────────────────────────────────────────────────────────
    @PostMapping("/notes-frais")
    @PreAuthorize("hasAnyRole('RH','FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<ExportJobResponse>> exportNoteFrais(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ExportNotefraisRequest req) {
        ExportJobResponse out = exportNotefraisService.exporterNoteFrais(req.missionId(), user.getOrganisationId(), user.getId());
        return ResponseEntity.status(out.id() == null ? 200 : 202).body(ApiResponse.ok(out));
    }

    // ── ÉTAT DE PAIE ──────────────────────────────────────────────────────────
    @PostMapping("/etats-paie/pdf")
    @PreAuthorize("hasAnyRole('RH','FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<ExportJobResponse>> exportEtatPaiePdf(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ExportEtatPaieRequest req) {
        ExportJobResponse out = exportEtatPaieService.exporterEtatPaiePdf(req, user.getOrganisationId(), user.getId());
        return ResponseEntity.status(out.id() == null ? 200 : 202).body(ApiResponse.ok(out));
    }

    @PostMapping("/etats-paie/excel")
    @PreAuthorize("hasAnyRole('RH','FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<ExportJobResponse>> exportEtatPaieExcel(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ExportEtatPaieRequest req) {
        ExportJobResponse out = exportEtatPaieService.exporterEtatPaieExcel(req, user.getOrganisationId(), user.getId());
        return ResponseEntity.status(out.id() == null ? 200 : 202).body(ApiResponse.ok(out));
    }

    // ── BUDGET ────────────────────────────────────────────────────────────────
    @PostMapping("/budget/pdf")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<ExportJobResponse>> exportBudgetPdf(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ExportBudgetRequest req) {
        ExportJobResponse out = exportBudgetService.exporterBudgetPdf(req, user.getOrganisationId(), user.getId());
        return ResponseEntity.status(out.id() == null ? 200 : 202).body(ApiResponse.ok(out));
    }

    @PostMapping("/budget/excel")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<ExportJobResponse>> exportBudgetExcel(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ExportBudgetRequest req) {
        ExportJobResponse out = exportBudgetService.exporterBudgetExcel(req, user.getOrganisationId(), user.getId());
        return ResponseEntity.status(out.id() == null ? 200 : 202).body(ApiResponse.ok(out));
    }

    // ── JOURNAL D'AUDIT ───────────────────────────────────────────────────────
    @PostMapping("/journal-audit/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExportJobResponse>> exportJournalAuditPdf(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ExportJournalAuditRequest req) {
        ExportJobResponse out = exportJournalAuditService.exporterJournalPdf(req, user.getOrganisationId(), user.getId());
        return ResponseEntity.status(out.id() == null ? 200 : 202).body(ApiResponse.ok(out));
    }

    @PostMapping("/journal-audit/excel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExportJobResponse>> exportJournalAuditExcel(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ExportJournalAuditRequest req) {
        ExportJobResponse out = exportJournalAuditService.exporterJournalExcel(req, user.getOrganisationId(), user.getId());
        return ResponseEntity.status(out.id() == null ? 200 : 202).body(ApiResponse.ok(out));
    }

    @PostMapping(value = "/journal-audit/csv", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StreamingResponseBody> exportJournalAuditCsv(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ExportJournalAuditRequest req) {
        // validations per spec
        if (req.dateDebut().isAfter(req.dateFin())) throw BusinessException.badRequest("EXPORT_PERIODE_INVALIDE");
        long days = java.time.temporal.ChronoUnit.DAYS.between(req.dateDebut(), req.dateFin());
        if (days > 366) throw BusinessException.badRequest("PERIODE_TROP_LARGE");

        UUID orgId = user.getOrganisationId();
        Instant start = req.dateDebut().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = req.dateFin().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        long count =
                auditLogRepository.countForExport(
                        orgId, start, end,
                        blankToNull(req.entite()), blankToNull(req.action()), req.utilisateurId());
        if (count == 0) throw BusinessException.badRequest("EXPORT_DONNEES_ABSENTES");

        StreamingResponseBody body =
                outputStream -> {
                    // BOM for Excel Windows
                    outputStream.write("\uFEFF".getBytes(StandardCharsets.UTF_8));
                    outputStream.write(
                            "date_action;utilisateur_id;action;entite;entite_id;champs_modifies;ip_address;avant_json;apres_json\n"
                                    .getBytes(StandardCharsets.UTF_8));

                    int page = 0;
                    int size = 1000;
                    while (true) {
                        var p = auditLogRepository.findForExport(
                                orgId, start, end,
                                blankToNull(req.entite()), blankToNull(req.action()), req.utilisateurId(),
                                PageRequest.of(page, size));
                        for (var a : p.getContent()) {
                            String line =
                                    escape(a.getDateAction() != null ? a.getDateAction().toString() : "")
                                            + ';'
                                            + escape(a.getUtilisateurId() != null ? a.getUtilisateurId().toString() : "")
                                            + ';'
                                            + escape(a.getAction())
                                            + ';'
                                            + escape(a.getEntite())
                                            + ';'
                                            + escape(a.getEntiteId() != null ? a.getEntiteId().toString() : "")
                                            + ';'
                                            + escape(ExportConformiteController.diffFields(a))
                                            + ';'
                                            + escape(a.getIpAddress())
                                            + ';'
                                            + escape(a.getAvant() != null ? a.getAvant().toString() : "")
                                            + ';'
                                            + escape(a.getApres() != null ? a.getApres().toString() : "")
                                            + "\n";
                            outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                        }
                        outputStream.flush();
                        if (p.isLast()) break;
                        page++;
                    }
                };

        String filename = "journal-audit-" + req.dateDebut() + "-" + req.dateFin() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(body);
    }

    // ── JOBS ──────────────────────────────────────────────────────────────────
    @GetMapping("/jobs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<ExportJobResponse>>> listJobs(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<ExportJobResponse> out =
                exportJobService.listJobsOrg(user.getOrganisationId(), user.getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ExportJobResponse>> getJob(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID jobId) {
        ExportJobResponse out = exportJobService.getJob(jobId, user.getOrganisationId());
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @DeleteMapping("/jobs/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteJob(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID jobId) {
        exportJobService.annulerJob(jobId, user.getOrganisationId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── CONFIG ────────────────────────────────────────────────────────────────
    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ConfigExport>> getConfig(@AuthenticationPrincipal CustomUserDetails user) {
        ConfigExport cfg =
                configExportRepository
                        .findByOrganisationId(user.getOrganisationId())
                        .orElseGet(
                                () -> {
                                    ConfigExport c = new ConfigExport();
                                    c.setOrganisationId(user.getOrganisationId());
                                    return configExportRepository.save(c);
                                });
        return ResponseEntity.ok(ApiResponse.ok(cfg));
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ConfigExport>> updateConfig(
            @AuthenticationPrincipal CustomUserDetails user, @RequestBody ConfigExportRequest req) {
        ConfigExport cfg =
                configExportRepository
                        .findByOrganisationId(user.getOrganisationId())
                        .orElseGet(
                                () -> {
                                    ConfigExport c = new ConfigExport();
                                    c.setOrganisationId(user.getOrganisationId());
                                    return c;
                                });

        if (req.piedPageMention() != null) cfg.setPiedPageMention(req.piedPageMention());
        if (req.couleurPrincipale() != null) cfg.setCouleurPrincipale(req.couleurPrincipale());
        if (req.seuilLignesSyncPdf() != null) cfg.setSeuilLignesSyncPdf(req.seuilLignesSyncPdf());
        if (req.seuilLignesSyncExcel() != null) cfg.setSeuilLignesSyncExcel(req.seuilLignesSyncExcel());
        if (req.watermarkActif() != null) cfg.setWatermarkActif(req.watermarkActif());
        if (req.watermarkTexte() != null) cfg.setWatermarkTexte(req.watermarkTexte());

        cfg.setOrganisationId(user.getOrganisationId());
        ConfigExport saved = configExportRepository.save(cfg);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    @PostMapping(value = "/config/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> uploadLogo(
            @AuthenticationPrincipal CustomUserDetails user, @RequestParam("logo") MultipartFile logo) {
        return uploadAsset(user, logo, "logo", "image/");
    }

    @PostMapping(value = "/config/cachet", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> uploadCachet(
            @AuthenticationPrincipal CustomUserDetails user, @RequestParam("cachet") MultipartFile cachet) {
        return uploadAsset(user, cachet, "cachet", "image/");
    }

    @PostMapping(value = "/config/signature-dg", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> uploadSignature(
            @AuthenticationPrincipal CustomUserDetails user, @RequestParam("signature") MultipartFile signature) {
        return uploadAsset(user, signature, "signature-dg", "image/");
    }

    private ResponseEntity<ApiResponse<String>> uploadAsset(CustomUserDetails user, MultipartFile file, String key, String expectedTypePrefix) {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("EXPORT_FICHIER_INVALIDE");
        if (file.getSize() > MAX_LOGO_BYTES) throw BusinessException.badRequest("EXPORT_FICHIER_TROP_GROS");
        if (file.getContentType() == null || !file.getContentType().startsWith(expectedTypePrefix)) {
            throw BusinessException.badRequest("EXPORT_FICHIER_TYPE_INVALIDE");
        }
        UUID orgId = user.getOrganisationId();
        String ext = guessExt(file.getOriginalFilename(), file.getContentType());
        String objectName = orgId + "/exports/config/" + key + "-" + System.currentTimeMillis() + ext;
        try {
            exportMinioService.uploadBytes(objectName, file.getBytes(), file.getContentType());
            String presigned = exportMinioService.presignGet(objectName, 7 * 24 * 3600);

            ConfigExport cfg = configExportRepository.findByOrganisationId(orgId).orElseGet(() -> {
                ConfigExport c = new ConfigExport();
                c.setOrganisationId(orgId);
                return c;
            });
            if ("logo".equals(key)) cfg.setLogoUrl(objectName);
            if ("cachet".equals(key)) cfg.setCachetOrgUrl(objectName);
            if ("signature-dg".equals(key)) cfg.setSignatureDgUrl(objectName);
            cfg.setOrganisationId(orgId);
            configExportRepository.save(cfg);

            return ResponseEntity.ok(ApiResponse.ok(presigned));
        } catch (Exception e) {
            throw new BusinessException("EXPORT_MINIO_ERROR", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Erreur lors de l'upload MinIO.");
        }
    }

    private static String guessExt(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int i = originalFilename.lastIndexOf('.');
            if (i >= 0) return originalFilename.substring(i);
        }
        if ("image/png".equals(contentType)) return ".png";
        if ("image/jpeg".equals(contentType)) return ".jpg";
        return ".bin";
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isBlank() ? null : v;
    }

    private static String escape(String s) {
        if (s == null) return "";
        boolean needQuotes = s.contains(";") || s.contains("\n") || s.contains("\r") || s.contains("\"");
        String out = s.replace("\"", "\"\"");
        return needQuotes ? "\"" + out + "\"" : out;
    }

    private static String diffFields(com.app.audit.entity.AuditLog a) {
        if (a.getAvant() == null && a.getApres() != null) return "Création";
        if (a.getAvant() != null && a.getApres() == null) return "Suppression";
        if (a.getAvant() == null || a.getApres() == null) return "";
        if (!a.getAvant().isObject() || !a.getApres().isObject()) return "";
        var before = a.getAvant();
        var after = a.getApres();
        var fields = new java.util.ArrayList<String>();
        before.fieldNames().forEachRemaining(
                f -> {
                    var b = before.get(f);
                    var n = after.get(f);
                    if (n != null && (b == null || !b.equals(n))) {
                        fields.add(f);
                    }
                });
        return String.join(",", fields);
    }
}

