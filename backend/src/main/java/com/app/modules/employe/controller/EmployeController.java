package com.app.modules.employe.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.employe.dto.EmployeCongeRequest;
import com.app.modules.notifications.dto.NotificationResponse;
import com.app.modules.notifications.service.NotificationService;
import com.app.modules.rh.dto.CongeRequest;
import com.app.modules.rh.dto.CongeResponse;
import com.app.modules.rh.dto.DroitsCongesDto;
import com.app.modules.rh.dto.PaieResponse;
import com.app.modules.rh.dto.SalarieResponse;
import com.app.modules.rh.service.CongeService;
import com.app.modules.rh.service.PaieService;
import com.app.modules.rh.service.SalarieService;
import com.app.shared.dto.ApiResponse;
import com.app.shared.dto.PageResponse;
import com.app.shared.storage.MinioStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employe")
@RequiredArgsConstructor
public class EmployeController {

    private final SalarieService salarieService;
    private final CongeService congeService;
    private final PaieService paieService;
    private final NotificationService notificationService;

    @GetMapping("/profil")
    @PreAuthorize("hasRole('EMPLOYE') or hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<SalarieResponse>> profil(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(salarieService.getMeStrict(user.getOrganisationId(), user.getId())));
    }

    @GetMapping("/conges")
    @PreAuthorize("hasRole('EMPLOYE') or hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<CongeResponse>>> mesConges(
            @AuthenticationPrincipal CustomUserDetails user,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<CongeResponse> page = congeService.listMesConges(user.getOrganisationId(), user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
    }

    @GetMapping("/droits-conges")
    @PreAuthorize("hasRole('EMPLOYE') or hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<DroitsCongesDto>> mesDroits(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(congeService.getMesDroitsConges(user.getOrganisationId(), user.getId())));
    }

    @PostMapping("/conges")
    @PreAuthorize("hasRole('EMPLOYE') or hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<CongeResponse>> soumettreConge(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody EmployeCongeRequest req
    ) {
        UUID salarieId = salarieService.getMeStrict(user.getOrganisationId(), user.getId()).id();
        CongeRequest body = new CongeRequest(salarieId, req.typeConge(), req.dateDebut(), req.dateFin(), req.commentaire());
        return ResponseEntity.ok(ApiResponse.ok(congeService.soumettre(body, false, user.getOrganisationId(), user.getId())));
    }

    @GetMapping("/paie/{annee}")
    @PreAuthorize("hasRole('EMPLOYE') or hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<PaieResponse>>> mesFichesPaie(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable int annee,
            @PageableDefault(size = 12) Pageable pageable
    ) {
        Page<PaieResponse> page = paieService.listMyPaie(user.getOrganisationId(), user.getId(), annee, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
    }

    /**
     * PDF fiche de paie individuelle.
     * Implémentation: réutilise le PDF de bulletin généré (iText) via le moteur paie.
     */
    @GetMapping("/paie/{annee}/{mois}/fiche-pdf")
    @PreAuthorize("hasRole('EMPLOYE') or hasAnyRole('RH','ADMIN')")
    public ResponseEntity<InputStreamResource> fichePdf(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable int annee,
            @PathVariable int mois
    ) throws Exception {
        MinioStorageService.Download dl = paieService.downloadMyBulletin(user.getOrganisationId(), user.getId(), user.getUsername(), annee, mois);
        String contentType = dl.contentType() != null && !dl.contentType().isBlank()
                ? dl.contentType()
                : MediaType.APPLICATION_PDF_VALUE;
        String safeName = ("fiche-paie-" + annee + "-" + (mois < 10 ? "0" + mois : mois) + ".pdf").replace("\"", "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(dl.stream()));
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasRole('EMPLOYE') or hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> mesNotifications(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(name = "nonLuesSeulement", defaultValue = "false") boolean nonLuesSeulement,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                notificationService.getMesNotifications(user.getId(), nonLuesSeulement, pageable)
        ));
    }
}

