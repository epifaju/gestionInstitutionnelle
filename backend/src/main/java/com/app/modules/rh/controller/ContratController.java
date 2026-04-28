package com.app.modules.rh.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.dto.*;
import com.app.modules.rh.entity.ConfigAlerteRh;
import com.app.modules.rh.service.ContratService;
import com.app.modules.rh.service.EcheanceService;
import com.app.modules.rh.service.FormationObligatoireService;
import com.app.modules.rh.service.TitreSejourService;
import com.app.modules.rh.service.VisiteMedicaleService;
import com.app.modules.rh.repository.ConfigAlerteRhRepository;
import com.app.shared.dto.ApiResponse;
import com.app.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rh/contrats")
@RequiredArgsConstructor
public class ContratController {

    private final ContratService contratService;
    private final EcheanceService echeanceService;
    private final VisiteMedicaleService visiteMedicaleService;
    private final TitreSejourService titreSejourService;
    private final FormationObligatoireService formationService;
    private final ConfigAlerteRhRepository configRepository;

    // ── Contrats ──
    @GetMapping
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ContratResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String typeContrat,
            @RequestParam(required = false) String service,
            Pageable p
    ) {
        Page<ContratResponse> page = contratService.listContrats(user.getOrganisationId(), typeContrat, service, p);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, x -> x)));
    }

    @PostMapping("/salaries/{salarieId}")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<ContratResponse>> creer(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID salarieId,
            @Valid @RequestBody ContratRequest req
    ) {
        ContratResponse res = contratService.creerContrat(req, salarieId, user.getOrganisationId(), user.getId());
        return ResponseEntity.status(201).body(ApiResponse.ok(res));
    }

    @GetMapping("/salaries/{salarieId}/actif")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<ContratResponse>> actif(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID salarieId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(contratService.getContratActif(salarieId, user.getOrganisationId())));
    }

    @GetMapping("/salaries/{salarieId}/historique")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<List<ContratResponse>>> historique(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID salarieId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(contratService.historiqueContrats(salarieId, user.getOrganisationId())));
    }

    @PostMapping("/{contratId}/renouveler")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<ApiResponse<ContratResponse>> renouveler(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID contratId,
            @Valid @RequestBody RenouvellementCddRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(contratService.renouvelerCdd(contratId, req, user.getOrganisationId(), user.getId())));
    }

    @PostMapping("/{contratId}/decision-fin")
    @PreAuthorize("hasRole('RH')")
    public ResponseEntity<ApiResponse<ContratResponse>> decisionFin(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID contratId,
            @Valid @RequestBody DecisionFinCddRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(contratService.enregistrerDecisionFin(contratId, req, user.getOrganisationId())));
    }

    @PostMapping(value = "/{contratId}/contrat-signe", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<String>> uploadContratSigne(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID contratId,
            @RequestParam("file") MultipartFile file
    ) {
        String url = contratService.uploadContratSigne(contratId, file, user.getOrganisationId());
        return ResponseEntity.ok(ApiResponse.ok(url));
    }

    @GetMapping("/cdd-expirant")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<List<ContratResponse>>> cddExpirant(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "90") int jours
    ) {
        return ResponseEntity.ok(ApiResponse.ok(contratService.getCddExpirantDans(user.getOrganisationId(), jours)));
    }

    // ── Échéances ──
    @GetMapping("/echeances")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<EcheanceResponse>>> listEcheances(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID salarieId,
            @RequestParam(required = false) LocalDate dateMin,
            @RequestParam(required = false) LocalDate dateMax,
            Pageable p
    ) {
        Page<EcheanceResponse> page = echeanceService.listEcheances(user.getOrganisationId(), statut, type, salarieId, dateMin, dateMax, p);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, x -> x)));
    }

    @PostMapping("/echeances")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<EcheanceResponse>> creerEcheance(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody EcheanceRequest req
    ) {
        EcheanceResponse res = echeanceService.creerEcheance(req, user.getOrganisationId(), user.getId());
        return ResponseEntity.status(201).body(ApiResponse.ok(res));
    }

    @GetMapping("/echeances/dashboard")
    @PreAuthorize("hasAnyRole('RH','ADMIN','FINANCIER')")
    public ResponseEntity<ApiResponse<EcheanceDashboardResponse>> dashboard(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(echeanceService.getDashboard(user.getOrganisationId())));
    }

    @PostMapping(value = "/echeances/{id}/traiter", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<EcheanceResponse>> traiter(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @RequestPart(name = "preuve", required = false) MultipartFile preuve,
            @Valid @RequestPart("data") TraiterEcheanceRequest data
    ) {
        return ResponseEntity.ok(ApiResponse.ok(echeanceService.traiterEcheance(id, data, preuve, user.getOrganisationId(), user.getId())));
    }

    @PostMapping("/echeances/{id}/annuler")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<EcheanceResponse>> annuler(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @RequestParam String motif
    ) {
        return ResponseEntity.ok(ApiResponse.ok(echeanceService.annulerEcheance(id, motif, user.getOrganisationId())));
    }

    // ── Visites médicales ──
    @GetMapping("/salaries/{salarieId}/visites")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<List<VisiteMedicaleResponse>>> visites(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID salarieId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(visiteMedicaleService.getVisitesSalarie(salarieId, user.getOrganisationId())));
    }

    @PostMapping("/salaries/{salarieId}/visites")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<VisiteMedicaleResponse>> creerVisite(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID salarieId,
            @Valid @RequestBody VisiteMedicaleRequest req
    ) {
        return ResponseEntity.status(201).body(ApiResponse.ok(visiteMedicaleService.creerVisite(salarieId, req, user.getOrganisationId())));
    }

    @PostMapping(value = "/visites/{id}/resultat", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<VisiteMedicaleResponse>> resultatVisite(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @RequestParam String resultat,
            @RequestParam(required = false) String restrictions,
            @RequestPart(required = false) MultipartFile compteRendu
    ) {
        return ResponseEntity.ok(ApiResponse.ok(visiteMedicaleService.enregistrerResultat(id, resultat, restrictions, compteRendu, user.getOrganisationId())));
    }

    // ── Titres de séjour ──
    @GetMapping("/salaries/{salarieId}/titres-sejour")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<List<TitreSejourResponse>>> titres(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID salarieId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(titreSejourService.getTitresSalarie(salarieId, user.getOrganisationId())));
    }

    @PostMapping(value = "/salaries/{salarieId}/titres-sejour", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<TitreSejourResponse>> creerTitre(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID salarieId,
            @Valid @RequestPart("data") TitreSejourRequest data,
            @RequestPart(name = "document", required = false) MultipartFile document
    ) {
        return ResponseEntity.status(201).body(ApiResponse.ok(titreSejourService.enregistrerTitre(salarieId, data, document, user.getOrganisationId())));
    }

    @PutMapping("/titres-sejour/{id}/statut-renouvellement")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<TitreSejourResponse>> statutTitre(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @RequestParam String statut
    ) {
        return ResponseEntity.ok(ApiResponse.ok(titreSejourService.mettreAJourStatutRenouvellement(id, statut, user.getOrganisationId())));
    }

    // ── Formations obligatoires ──
    @GetMapping("/salaries/{salarieId}/formations")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<List<FormationObligatoireResponse>>> formations(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID salarieId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(formationService.getFormationsSalarie(salarieId, user.getOrganisationId())));
    }

    @PostMapping(value = "/salaries/{salarieId}/formations", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<FormationObligatoireResponse>> creerFormation(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID salarieId,
            @Valid @RequestPart("data") FormationObligatoireRequest data,
            @RequestPart(name = "certificat", required = false) MultipartFile certificat
    ) {
        return ResponseEntity.status(201).body(ApiResponse.ok(formationService.enregistrerFormation(salarieId, data, certificat, user.getOrganisationId())));
    }

    @PostMapping(value = "/formations/{id}/renouveler", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<FormationObligatoireResponse>> renouvelerFormation(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestPart("data") FormationObligatoireRequest data,
            @RequestPart(name = "certificat", required = false) MultipartFile certificat
    ) {
        return ResponseEntity.status(201).body(ApiResponse.ok(formationService.renouvelerFormation(id, data, certificat, user.getOrganisationId())));
    }

    // ── Configuration ──
    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ConfigAlerteRh>> getConfig(@AuthenticationPrincipal CustomUserDetails user) {
        ConfigAlerteRh cfg = configRepository.findByOrganisationId(user.getOrganisationId())
                .orElseGet(() -> {
                    ConfigAlerteRh c = new ConfigAlerteRh();
                    c.setOrganisationId(user.getOrganisationId());
                    return configRepository.save(c);
                });
        return ResponseEntity.ok(ApiResponse.ok(cfg));
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ConfigAlerteRh>> updateConfig(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ConfigAlerteRequest req
    ) {
        ConfigAlerteRh cfg = configRepository.findByOrganisationId(user.getOrganisationId())
                .orElseGet(() -> {
                    ConfigAlerteRh c = new ConfigAlerteRh();
                    c.setOrganisationId(user.getOrganisationId());
                    return c;
                });

        if (req.alerteFinCddJ() != null) cfg.setAlerteFinCddJ(req.alerteFinCddJ());
        if (req.alertePeriodeEssaiJ() != null) cfg.setAlertePeriodeEssaiJ(req.alertePeriodeEssaiJ());
        if (req.alerteVisiteMedJ() != null) cfg.setAlerteVisiteMedJ(req.alerteVisiteMedJ());
        if (req.alerteTitreSejourJ() != null) cfg.setAlerteTitreSejourJ(req.alerteTitreSejourJ());
        if (req.alerteFormationJ() != null) cfg.setAlerteFormationJ(req.alerteFormationJ());
        cfg.setNotifierRh(req.notifierRh());
        cfg.setNotifierManager(req.notifierManager());
        cfg.setNotifierSalarie(req.notifierSalarie());
        if (req.maxRenouvellementsCdd() != null) cfg.setMaxRenouvellementsCdd(req.maxRenouvellementsCdd());

        cfg = configRepository.save(cfg);
        return ResponseEntity.ok(ApiResponse.ok(cfg));
    }
}

