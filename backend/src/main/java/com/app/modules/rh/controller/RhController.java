package com.app.modules.rh.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.dto.CongeRequest;
import com.app.modules.rh.dto.CongeResponse;
import com.app.modules.rh.dto.CongeValidationRequest;
import com.app.modules.rh.dto.DocumentUrlResponse;
import com.app.modules.rh.dto.DroitsCongesDto;
import com.app.modules.rh.dto.GrilleSalarialeRequest;
import com.app.modules.rh.dto.HistoriqueSalaireResponse;
import com.app.modules.rh.dto.MarquerPayeRequest;
import com.app.modules.rh.dto.PaieResponse;
import com.app.modules.rh.dto.SalarieRequest;
import com.app.modules.rh.dto.SalarieResponse;
import com.app.modules.rh.service.CongeService;
import com.app.modules.rh.service.PaieService;
import com.app.modules.rh.service.SalarieService;
import com.app.shared.dto.ApiResponse;
import com.app.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rh")
@RequiredArgsConstructor
public class RhController {

        private final SalarieService salarieService;
        private final CongeService congeService;
        private final PaieService paieService;

        @GetMapping("/salaries")
        @PreAuthorize("hasAnyRole('RH','ADMIN')")
        public ResponseEntity<ApiResponse<PageResponse<SalarieResponse>>> listSalaries(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @RequestParam(required = false) String statut,
                        @RequestParam(required = false) String service,
                        @RequestParam(required = false) String search,
                        @PageableDefault(size = 20) Pageable pageable) {
                Page<SalarieResponse> page = salarieService.listSalaries(user.getOrganisationId(), statut, service,
                                search, pageable);
                return ResponseEntity.ok(
                                ApiResponse.ok(PageResponse.from(page, r -> r)));
        }

        @PostMapping("/salaries")
        @PreAuthorize("hasAnyRole('RH','ADMIN')")
        public ResponseEntity<ApiResponse<SalarieResponse>> creerSalarie(
                        @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody SalarieRequest req) {
                return ResponseEntity.ok(ApiResponse.ok(salarieService.creer(req, user.getOrganisationId())));
        }

        @GetMapping("/salaries/{id}")
        @PreAuthorize("hasAnyRole('RH','ADMIN') or @securityService.isSelf(#id,authentication)")
        public ResponseEntity<ApiResponse<SalarieResponse>> getSalarie(
                        @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
                return ResponseEntity.ok(ApiResponse.ok(salarieService.getById(id, user.getOrganisationId())));
        }

        @GetMapping("/me/salarie")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<SalarieResponse>> meSalarie(
                        @AuthenticationPrincipal CustomUserDetails user) {
                return ResponseEntity.ok(ApiResponse.ok(
                        salarieService.getMe(user.getOrganisationId(), user.getId(), user.getUsername())));
        }

        @PutMapping("/salaries/{id}")
        @PreAuthorize("hasAnyRole('RH','ADMIN')")
        public ResponseEntity<ApiResponse<SalarieResponse>> modifierSalarie(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @PathVariable UUID id,
                        @Valid @RequestBody SalarieRequest req) {
                return ResponseEntity.ok(ApiResponse.ok(salarieService.modifier(id, req, user.getOrganisationId())));
        }

        @PostMapping("/salaries/{id}/valider")
        @PreAuthorize("hasRole('RH')")
        public ResponseEntity<ApiResponse<SalarieResponse>> validerDossier(
                        @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
                return ResponseEntity.ok(ApiResponse.ok(salarieService.validerDossier(id, user.getOrganisationId())));
        }

        @PostMapping("/salaries/{id}/documents")
        @PreAuthorize("hasAnyRole('RH','ADMIN')")
        public ResponseEntity<ApiResponse<String>> uploadDocument(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @PathVariable UUID id,
                        @RequestParam("file") MultipartFile file)
                        throws Exception {
                return ResponseEntity.ok(
                                ApiResponse.ok(salarieService.uploadContrat(id, file, user.getOrganisationId())));
        }

        @GetMapping("/salaries/{id}/documents")
        @PreAuthorize("hasAnyRole('RH','ADMIN')")
        public ResponseEntity<ApiResponse<List<DocumentUrlResponse>>> listDocuments(
                        @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) throws Exception {
                return ResponseEntity.ok(
                                ApiResponse.ok(salarieService.listDocuments(id, user.getOrganisationId())));
        }

        @GetMapping("/salaries/{id}/historique-salaires")
        @PreAuthorize("hasAnyRole('RH','ADMIN')")
        public ResponseEntity<ApiResponse<List<HistoriqueSalaireResponse>>> historiqueSalaires(
                        @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
                return ResponseEntity.ok(
                                ApiResponse.ok(salarieService.listHistoriqueSalaires(id, user.getOrganisationId())));
        }

        @PostMapping("/salaries/{id}/grille-salariale")
        @PreAuthorize("hasAnyRole('RH','ADMIN')")
        public ResponseEntity<ApiResponse<SalarieResponse>> grilleSalariale(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @PathVariable UUID id,
                        @Valid @RequestBody GrilleSalarialeRequest req) {
                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                salarieService.ajouterGrilleSalariale(
                                                                id,
                                                                req.brut(),
                                                                req.net(),
                                                                req.devise(),
                                                                req.dateDebut(),
                                                                user.getOrganisationId())));
        }

        @GetMapping("/salaries/{id}/droits-conges/{annee}")
        @PreAuthorize("hasAnyRole('RH','ADMIN') or @securityService.isSelf(#id,authentication)")
        public ResponseEntity<ApiResponse<DroitsCongesDto>> droitsConges(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @PathVariable UUID id,
                        @PathVariable int annee) {
                return ResponseEntity.ok(
                                ApiResponse.ok(congeService.getDroits(id, annee, user.getOrganisationId())));
        }

        @GetMapping("/conges")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<PageResponse<CongeResponse>>> listConges(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @RequestParam(required = false) String statut,
                        @RequestParam(required = false) String typeConge,
                        @RequestParam(required = false) LocalDate debut,
                        @RequestParam(required = false) LocalDate fin,
                        @RequestParam(required = false) String service,
                        @RequestParam(required = false) UUID salarieId,
                        @PageableDefault(size = 20) Pageable pageable) {
                Page<CongeResponse> page = congeService.listConges(
                                user.getOrganisationId(), statut, typeConge, debut, fin, service, salarieId, pageable);
                return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
        }

        @PostMapping("/conges")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<CongeResponse>> soumettreConge(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @RequestParam(required = false, defaultValue = "false") boolean draft,
                        @Valid @RequestBody CongeRequest req) {
                return ResponseEntity.ok(
                                ApiResponse.ok(congeService.soumettre(req, draft, user.getOrganisationId(),
                                                user.getId())));
        }

        @PostMapping("/conges/{id}/soumettre")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<CongeResponse>> soumettreBrouillon(
                        @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
                return ResponseEntity.ok(
                                ApiResponse.ok(congeService.soumettreBrouillon(id, user.getOrganisationId(),
                                                user.getId())));
        }

        @GetMapping("/conges/calendrier")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<List<CongeResponse>>> calendrier(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @RequestParam LocalDate debut,
                        @RequestParam LocalDate fin) {
                return ResponseEntity.ok(
                                ApiResponse.ok(congeService.getCalendrier(user.getOrganisationId(), debut, fin)));
        }

        @PostMapping("/conges/{id}/valider")
        @PreAuthorize("hasAnyRole('RH','ADMIN')")
        public ResponseEntity<ApiResponse<CongeResponse>> validerConge(
                        @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
                return ResponseEntity.ok(
                                ApiResponse.ok(congeService.valider(id, user.getId(), user.getOrganisationId())));
        }

        @PostMapping("/conges/{id}/rejeter")
        @PreAuthorize("hasAnyRole('RH','ADMIN')")
        public ResponseEntity<ApiResponse<CongeResponse>> rejeterConge(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @PathVariable UUID id,
                        @Valid @RequestBody CongeValidationRequest req) {
                return ResponseEntity.ok(ApiResponse.ok(congeService.rejeter(id, req, user.getOrganisationId())));
        }

        @PostMapping("/conges/{id}/annuler")
        @PreAuthorize("hasAnyRole('RH','ADMIN')")
        public ResponseEntity<ApiResponse<CongeResponse>> annulerCongeValide(
                        @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
                return ResponseEntity.ok(ApiResponse.ok(congeService.annulerValide(id, user.getOrganisationId())));
        }

        @GetMapping("/paie")
        @PreAuthorize("hasAnyRole('RH','ADMIN','FINANCIER')")
        public ResponseEntity<ApiResponse<PageResponse<PaieResponse>>> paieOrganisation(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @RequestParam int annee,
                        @PageableDefault(size = 24) Pageable pageable) {
                Page<PaieResponse> page = paieService.listPaieOrganisation(user.getOrganisationId(), annee, pageable);
                return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
        }

        @GetMapping("/me/paie")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<PageResponse<PaieResponse>>> myPaie(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @RequestParam int annee,
                        @PageableDefault(size = 12) Pageable pageable) {
                Page<PaieResponse> page = paieService.listMyPaie(user.getOrganisationId(), user.getId(), annee, pageable);
                return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
        }

        @GetMapping("/paie/{salarieId}/{annee}")
        @PreAuthorize("hasAnyRole('RH','ADMIN','FINANCIER')")
        public ResponseEntity<ApiResponse<PageResponse<PaieResponse>>> paieAnnuelle(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @PathVariable UUID salarieId,
                        @PathVariable int annee,
                        @PageableDefault(size = 12) Pageable pageable) {
                Page<PaieResponse> page = paieService.getPaieAnnuelle(salarieId, annee, user.getOrganisationId(),
                                pageable);
                return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
        }

        @PutMapping("/paie/{id}/marquer-paye")
        @PreAuthorize("hasAnyRole('RH','FINANCIER','ADMIN')")
        public ResponseEntity<ApiResponse<PaieResponse>> marquerPaye(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @PathVariable UUID id,
                        @Valid @RequestBody MarquerPayeRequest req) {
                return ResponseEntity.ok(ApiResponse.ok(paieService.marquerPaye(id, req, user.getOrganisationId())));
        }

        @PostMapping("/paie/{id}/annuler")
        @PreAuthorize("hasAnyRole('RH','FINANCIER','ADMIN')")
        public ResponseEntity<ApiResponse<PaieResponse>> annulerPaie(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @PathVariable UUID id) {
                return ResponseEntity.ok(ApiResponse.ok(paieService.annuler(id, user.getOrganisationId())));
        }
}
