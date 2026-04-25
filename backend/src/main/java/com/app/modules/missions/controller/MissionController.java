package com.app.modules.missions.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.missions.dto.FraisRequest;
import com.app.modules.missions.dto.FraisResponse;
import com.app.modules.missions.dto.MissionRequest;
import com.app.modules.missions.dto.MissionResponse;
import com.app.modules.missions.service.MissionService;
import com.app.modules.rh.service.SalarieService;
import com.app.shared.dto.ApiResponse;
import com.app.shared.dto.PageResponse;
import com.app.shared.storage.MinioStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;
    private final SalarieService salarieService;
    private final MinioStorageService minioStorageService;

    private UUID resolveMySalarieId(UUID orgId, CustomUserDetails user) {
        // Avoid hard failure "SALARIE_NON_LIE" for EMPLOYE accounts not strictly linked yet:
        // fallback to email match (same logic as employee portal tolerant paths).
        return salarieService.getMe(orgId, user.getId(), user.getUsername()).id();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<MissionResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) UUID salarieId,
            @RequestParam(required = false) LocalDate debut,
            @RequestParam(required = false) LocalDate fin,
            Pageable pageable) {
        UUID orgId = user.getOrganisationId();
        String role = user.getUtilisateur() != null && user.getUtilisateur().getRole() != null ? user.getUtilisateur().getRole().name() : "";
        boolean canListAll = "ADMIN".equals(role) || "RH".equals(role) || "FINANCIER".equals(role);
        UUID effectiveSalarieId = salarieId;
        if (!canListAll) {
            effectiveSalarieId = resolveMySalarieId(orgId, user);
        }
        Page<MissionResponse> page = missionService.list(orgId, statut, effectiveSalarieId, debut, fin, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, x -> x)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MissionResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody MissionRequest req) {
        UUID orgId = user.getOrganisationId();
        UUID salarieId = resolveMySalarieId(orgId, user);
        return ResponseEntity.ok(ApiResponse.ok(missionService.creer(req, orgId, salarieId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MissionResponse>> get(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(missionService.getById(id, user.getOrganisationId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MissionResponse>> update(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id, @Valid @RequestBody MissionRequest req) {
        UUID orgId = user.getOrganisationId();
        UUID salarieId = resolveMySalarieId(orgId, user);
        return ResponseEntity.ok(ApiResponse.ok(missionService.update(id, req, orgId, salarieId)));
    }

    @PostMapping("/{id}/soumettre")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MissionResponse>> soumettre(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        UUID orgId = user.getOrganisationId();
        UUID salarieId = resolveMySalarieId(orgId, user);
        return ResponseEntity.ok(ApiResponse.ok(missionService.soumettre(id, orgId, salarieId)));
    }

    @PostMapping("/{id}/approuver")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<MissionResponse>> approuver(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        UUID orgId = user.getOrganisationId();
        UUID approbateurId = user.getId();
        BigDecimal avanceVersee = null;
        if (body != null && body.get("avanceVersee") != null) {
            avanceVersee = new BigDecimal(String.valueOf(body.get("avanceVersee")));
        }
        return ResponseEntity.ok(ApiResponse.ok(missionService.approuver(id, approbateurId, avanceVersee, orgId)));
    }

    @PostMapping("/{id}/refuser")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<MissionResponse>> refuser(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID orgId = user.getOrganisationId();
        String motif = body == null ? null : (String) body.get("motifRefus");
        return ResponseEntity.ok(ApiResponse.ok(missionService.refuser(id, motif, orgId)));
    }

    @PostMapping("/{id}/terminer")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<MissionResponse>> terminer(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(missionService.terminer(id, user.getOrganisationId())));
    }

    @PostMapping("/{id}/ordre-mission")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadOrdre(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id, @RequestParam("file") MultipartFile file) throws Exception {
        String url = missionService.uploadOrdreMission(id, file, user.getOrganisationId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    @PostMapping("/{id}/rapport")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadRapport(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id, @RequestParam("file") MultipartFile file) throws Exception {
        String url = missionService.uploadRapport(id, file, user.getOrganisationId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    @PostMapping("/{id}/frais")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FraisResponse>> ajouterFrais(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestPart("req") FraisRequest req,
            @RequestPart(name = "justificatif", required = false) MultipartFile justificatif) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(missionService.ajouterFrais(id, req, justificatif, user.getOrganisationId())));
    }

    @PostMapping("/{id}/frais/{fraisId}/valider")
    @PreAuthorize("hasAnyRole('RH','FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<FraisResponse>> valider(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id, @PathVariable UUID fraisId) {
        return ResponseEntity.ok(ApiResponse.ok(missionService.validerFrais(id, fraisId, user.getOrganisationId())));
    }

    @PostMapping("/{id}/frais/{fraisId}/rembourser")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<FraisResponse>> rembourser(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id, @PathVariable UUID fraisId) {
        return ResponseEntity.ok(ApiResponse.ok(missionService.rembourserFrais(id, fraisId, user.getOrganisationId(), user.getId())));
    }

    @GetMapping("/{id}/frais/{fraisId}/justificatif")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> downloadJustificatif(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id, @PathVariable UUID fraisId) throws Exception {
        String objectName = missionService.getFraisJustificatifObjectName(id, fraisId, user.getOrganisationId());
        MinioStorageService.Download dl = minioStorageService.download(objectName);
        return asDownload(dl);
    }

    @GetMapping("/{id}/ordre-mission")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> downloadOrdre(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) throws Exception {
        String objectName = missionService.getOrdreObjectName(id, user.getOrganisationId());
        MinioStorageService.Download dl = minioStorageService.download(objectName);
        return asDownload(dl);
    }

    @GetMapping("/{id}/rapport")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> downloadRapport(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) throws Exception {
        String objectName = missionService.getRapportObjectName(id, user.getOrganisationId());
        MinioStorageService.Download dl = minioStorageService.download(objectName);
        return asDownload(dl);
    }

    private static ResponseEntity<InputStreamResource> asDownload(MinioStorageService.Download dl) {
        MediaType mt = (dl.contentType() != null && !dl.contentType().isBlank())
                ? MediaType.parseMediaType(dl.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mt)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.objectName().replaceAll("^.*/", "") + "\"")
                .body(new InputStreamResource(dl.stream()));
    }
}

