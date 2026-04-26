package com.app.modules.finance.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.finance.dto.CategorieRequest;
import com.app.modules.finance.dto.CategorieResponse;
import com.app.modules.finance.dto.ChangerStatutRequest;
import com.app.modules.finance.dto.FactureRequest;
import com.app.modules.finance.dto.FactureResponse;
import com.app.modules.finance.dto.PaiementRequest;
import com.app.modules.finance.dto.PaiementResponse;
import com.app.modules.finance.dto.RecetteRequest;
import com.app.modules.finance.dto.RecetteResponse;
import com.app.modules.finance.dto.StatsResponse;
import com.app.modules.finance.dto.TauxChangeResponse;
import com.app.modules.finance.dto.TauxChangeUpsertRequest;
import com.app.modules.finance.service.CategorieService;
import com.app.modules.finance.service.FactureService;
import com.app.modules.finance.service.PaiementService;
import com.app.modules.finance.service.RecetteService;
import com.app.modules.finance.service.StatsService;
import com.app.modules.finance.service.TauxChangeService;
import com.app.shared.dto.ApiResponse;
import com.app.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FactureService factureService;
    private final PaiementService paiementService;
    private final RecetteService recetteService;
    private final StatsService statsService;
    private final CategorieService categorieService;
    private final TauxChangeService tauxChangeService;

    @GetMapping("/factures")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<FactureResponse>>> listFactures(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) UUID categorieId,
            @RequestParam(required = false) LocalDate debut,
            @RequestParam(required = false) LocalDate fin,
            @RequestParam(required = false) String fournisseur,
            @RequestParam(required = false) BigDecimal montantMin,
            @RequestParam(required = false) BigDecimal montantMax,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<FactureResponse> page =
                factureService.list(
                        user.getOrganisationId(), statut, categorieId, debut, fin, fournisseur, montantMin, montantMax, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
    }

    @PostMapping(value = "/factures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<FactureResponse>> creerFacture(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestPart("facture") FactureRequest req,
            @RequestPart(value = "justificatif", required = false) MultipartFile justificatif)
            throws Exception {
        return ResponseEntity.ok(
                ApiResponse.ok(factureService.creer(req, justificatif, user.getOrganisationId(), user.getId())));
    }

    @GetMapping("/factures/{id}")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<FactureResponse>> getFacture(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(factureService.getById(id, user.getOrganisationId())));
    }

    @PutMapping("/factures/{id}")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<FactureResponse>> modifierFacture(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody FactureRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(factureService.modifier(id, req, user.getOrganisationId(), user.getId())));
    }

    @PutMapping("/factures/{id}/statut")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<FactureResponse>> changerStatut(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody ChangerStatutRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        factureService.changerStatut(id, req.nouveauStatut(), user.getOrganisationId(), user.getId())));
    }

    @PostMapping(value = "/factures/{id}/justificatif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<Object>> uploadJustificatif(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file)
            throws Exception {
        factureService.uploadJustificatif(id, file, user.getOrganisationId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok("OK", null));
    }

    @GetMapping("/factures/{id}/justificatif")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<byte[]> downloadJustificatifFacture(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) throws Exception {
        String objectName = factureService.getJustificatifObjectName(id, user.getOrganisationId());
        var dl = factureService.downloadJustificatif(objectName);
        String filename = objectName.substring(objectName.lastIndexOf('/') + 1);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(dl.contentType() != null ? dl.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE));
        headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
        headers.setContentLength(dl.size());
        byte[] bytes;
        try (var is = dl.stream()) {
            bytes = is.readAllBytes();
        }
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @GetMapping("/paiements")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<PaiementResponse>>> listPaiements(
            @AuthenticationPrincipal CustomUserDetails user, @PageableDefault(size = 20) Pageable pageable) {
        Page<PaiementResponse> page = paiementService.list(user.getOrganisationId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
    }

    @PostMapping("/paiements")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<PaiementResponse>> enregistrerPaiement(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody PaiementRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(paiementService.enregistrer(req, user.getOrganisationId(), user.getId())));
    }

    @GetMapping("/recettes")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<RecetteResponse>>> listRecettes(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) LocalDate debut,
            @RequestParam(required = false) LocalDate fin,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<RecetteResponse> page = recetteService.list(user.getOrganisationId(), type, debut, fin, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
    }

    @PostMapping(value = "/recettes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<RecetteResponse>> creerRecette(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestPart("recette") RecetteRequest req,
            @RequestPart(value = "justificatif", required = false) MultipartFile justificatif)
            throws Exception {
        return ResponseEntity.ok(
                ApiResponse.ok(recetteService.creer(req, justificatif, user.getOrganisationId(), user.getId())));
    }

    @PutMapping("/recettes/{id}")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<RecetteResponse>> modifierRecette(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody RecetteRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(recetteService.modifier(id, req, user.getOrganisationId(), user.getId())));
    }

    @DeleteMapping("/recettes/{id}")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<Object>> supprimerRecette(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        recetteService.supprimer(id, user.getOrganisationId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok("OK", null));
    }

    @GetMapping("/recettes/{id}/justificatif")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<byte[]> downloadJustificatifRecette(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) throws Exception {
        String objectName = recetteService.getJustificatifObjectName(id, user.getOrganisationId());
        var dl = recetteService.downloadJustificatif(objectName);
        String filename = objectName.substring(objectName.lastIndexOf('/') + 1);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(dl.contentType() != null ? dl.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE));
        headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
        headers.setContentLength(dl.size());
        byte[] bytes;
        try (var is = dl.stream()) {
            bytes = is.readAllBytes();
        }
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PostMapping(value = "/recettes/{id}/justificatif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<Object>> uploadJustificatifRecette(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) throws Exception {
        recetteService.uploadJustificatif(id, file, user.getOrganisationId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok("OK", null));
    }

    @GetMapping("/stats/{annee}/{mois}")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<StatsResponse>> stats(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable int annee, @PathVariable int mois) {
        return ResponseEntity.ok(
                ApiResponse.ok(statsService.getStatsMensuelles(user.getOrganisationId(), annee, mois)));
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CategorieResponse>>> categories(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.ok(categorieService.list(user.getOrganisationId(), includeInactive)));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIER')")
    public ResponseEntity<ApiResponse<CategorieResponse>> creerCategorie(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody CategorieRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(categorieService.creer(req, user.getOrganisationId())));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIER')")
    public ResponseEntity<ApiResponse<CategorieResponse>> modifierCategorie(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody CategorieRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(categorieService.modifier(id, req, user.getOrganisationId())));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIER')")
    public ResponseEntity<ApiResponse<Object>> supprimerCategorie(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id) {
        categorieService.supprimer(id, user.getOrganisationId());
        return ResponseEntity.ok(ApiResponse.ok("OK", null));
    }

    @PostMapping("/categories/{id}/reactiver")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIER')")
    public ResponseEntity<ApiResponse<Object>> reactiverCategorie(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        categorieService.reactiver(id, user.getOrganisationId());
        return ResponseEntity.ok(ApiResponse.ok("OK", null));
    }

    @GetMapping("/taux-change")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<List<TauxChangeResponse>>> listTauxChange(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam LocalDate date
    ) {
        var list = tauxChangeService.listForDate(user.getOrganisationId(), date).stream()
                .map(t -> new TauxChangeResponse(t.getDate(), t.getDevise(), t.getTauxVersEur()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PutMapping("/taux-change")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<TauxChangeResponse>> upsertTauxChange(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody TauxChangeUpsertRequest req
    ) {
        var tc = tauxChangeService.upsert(user.getOrganisationId(), req.date(), req.devise(), req.tauxVersEur());
        return ResponseEntity.ok(ApiResponse.ok(new TauxChangeResponse(tc.getDate(), tc.getDevise(), tc.getTauxVersEur())));
    }
}
