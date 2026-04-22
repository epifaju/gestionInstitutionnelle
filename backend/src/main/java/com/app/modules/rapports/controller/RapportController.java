package com.app.modules.rapports.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rapports.dto.BilanMensuelCompletResponse;
import com.app.modules.rapports.dto.DashboardResponse;
import com.app.modules.rapports.service.RapportService;
import com.app.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rapports")
@RequiredArgsConstructor
public class RapportController {

        private final RapportService rapportService;

        @GetMapping("/dashboard")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<DashboardResponse>> dashboard(
                        @AuthenticationPrincipal CustomUserDetails user) {
                return ResponseEntity.ok(
                                ApiResponse.ok(
                                                rapportService.getDashboard(
                                                                user.getOrganisationId(),
                                                                user.getId(),
                                                                user.getUtilisateur().getRole())));
        }

        @GetMapping("/bilan-mensuel/{annee}/{mois}")
        @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
        public ResponseEntity<ApiResponse<BilanMensuelCompletResponse>> bilanMensuel(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @PathVariable int annee,
                        @PathVariable int mois) {
                return ResponseEntity.ok(
                                ApiResponse.ok(rapportService.getBilanMensuel(user.getOrganisationId(), annee, mois)));
        }

        @GetMapping("/bilan-mensuel/{annee}/{mois}/pdf")
        @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
        public ResponseEntity<byte[]> bilanMensuelPdf(
                        @AuthenticationPrincipal CustomUserDetails user,
                        @PathVariable int annee,
                        @PathVariable int mois) {
                byte[] pdf = rapportService.exportBilanMensuelPdf(
                                user.getOrganisationId(), annee, mois, user.getOrganisationNom());
                String filename = String.format("bilan-%d-%02d.pdf", annee, mois);
                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                                .contentType(MediaType.APPLICATION_PDF)
                                .body(pdf);
        }

        @GetMapping("/bilan-annuel/{annee}/excel")
        @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
        public ResponseEntity<byte[]> bilanAnnuelExcel(
                        @AuthenticationPrincipal CustomUserDetails user, @PathVariable int annee) {
                byte[] xlsx = rapportService.exportBilanAnnuelExcel(user.getOrganisationId(), annee);
                String filename = String.format("bilan-annuel-%d.xlsx", annee);
                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                                .contentType(
                                                MediaType.parseMediaType(
                                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                .body(xlsx);
        }

        @GetMapping("/export-csv/{entite}")
        @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
        public ResponseEntity<byte[]> exportCsv(
                        @AuthenticationPrincipal CustomUserDetails user, @PathVariable String entite) {
                byte[] csv = rapportService.exportCsv(user.getOrganisationId(), entite);
                String filename = "export-" + entite + ".csv";
                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                                .body(csv);
        }
}
