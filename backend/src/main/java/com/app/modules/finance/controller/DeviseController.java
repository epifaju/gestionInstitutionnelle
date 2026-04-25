package com.app.modules.finance.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.finance.entity.TauxChangeHistorique;
import com.app.modules.finance.repository.TauxChangeHistoriqueRepository;
import com.app.modules.finance.service.ExchangeRateService;
import com.app.shared.dto.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devises")
@RequiredArgsConstructor
public class DeviseController {

    private final ExchangeRateService exchangeRateService;
    private final TauxChangeHistoriqueRepository historiqueRepository;

    @GetMapping("/taux-du-jour")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> tauxDuJour(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "EUR") String base) {
        // If base->EUR needed and API down/unsupported, we can fallback to org manual table
        return ResponseEntity.ok(ApiResponse.ok(exchangeRateService.getTousLesTauxDuJour(base)));
    }

    @GetMapping("/taux/{devise1}/{devise2}/{date}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BigDecimal>> tauxALaDate(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String devise1,
            @PathVariable String devise2,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(exchangeRateService.getTauxALaDate(user.getOrganisationId(), devise1, devise2, date)));
    }

    @GetMapping("/convertir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> convertir(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam BigDecimal montant,
            @RequestParam(name = "de") @NotBlank String de,
            @RequestParam(name = "vers") @NotBlank String vers,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        BigDecimal resultat = exchangeRateService.convertir(user.getOrganisationId(), montant, de, vers, date);
        BigDecimal taux = date == null
                ? exchangeRateService.getTauxDuJour(user.getOrganisationId(), de, vers)
                : exchangeRateService.getTauxALaDate(user.getOrganisationId(), de, vers, date);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("montant", montant, "de", de, "vers", vers, "date", date, "taux", taux, "resultat", resultat)));
    }

    @GetMapping("/historique/{devise1}/{devise2}")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<List<TauxChangeHistorique>>> historique(
            @PathVariable String devise1,
            @PathVariable String devise2,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate d = debut != null ? debut : LocalDate.now().minusDays(30);
        LocalDate f = fin != null ? fin : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(historiqueRepository.findByDeviseBaseAndDeviseCibleAndDateTauxBetweenOrderByDateTauxAsc(devise1.trim().toUpperCase(), devise2.trim().toUpperCase(), d, f)));
    }
}

