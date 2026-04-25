package com.app.modules.finance.service;

import com.app.modules.finance.entity.TauxChangeHistorique;
import com.app.modules.finance.repository.TauxChangeHistoriqueRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private static final String BASE_URL = "https://api.frankfurter.app";

    // Devises prioritaires (UI + refresh auto)
    private static final List<String> DEFAULT_TARGETS =
            List.of("EUR", "USD", "GBP", "CHF", "XOF", "MAD", "DZD", "TND", "JPY", "CNY");

    private final TauxChangeHistoriqueRepository historiqueRepository;
    private final TauxChangeService tauxChangeService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @Cacheable(cacheNames = "fx_taux_du_jour", key = "#deviseBase + ':' + #deviseCible")
    @Transactional
    public BigDecimal getTauxDuJour(String deviseBase, String deviseCible) {
        LocalDate today = LocalDate.now();
        String base = normalize(deviseBase);
        String cible = normalize(deviseCible);
        if (base.equals(cible)) return BigDecimal.ONE;

        // If already stored today, use it.
        return historiqueRepository
                .findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc(base, cible, today)
                .map(TauxChangeHistorique::getTaux)
                .orElseGet(() -> {
                    try {
                        BigDecimal rate = fetchRateForDate(base, cible, "latest");
                        persist(base, cible, today, rate, "API");
                        return rate;
                    } catch (Exception ex) {
                        // fallback: last known rate in DB
                        return historiqueRepository
                                .findTopByDeviseBaseAndDeviseCibleAndDateTauxLessThanEqualOrderByDateTauxDescCreatedAtDesc(
                                        base, cible, today)
                                .map(TauxChangeHistorique::getTaux)
                                .orElseThrow(
                                        () ->
                                                new BusinessException(
                                                        "TAUX_CHANGE_INDISPONIBLE",
                                                        HttpStatus.SERVICE_UNAVAILABLE,
                                                        "Taux de change indisponible (API et historique)."));
                    }
                });
    }

    @Transactional
    public BigDecimal getTauxDuJour(UUID orgId, String deviseBase, String deviseCible) {
        try {
            return getTauxDuJour(deviseBase, deviseCible);
        } catch (BusinessException be) {
            // Fallback to manual rates (Finance → Taux de change) when API is unavailable or currency unsupported.
            if (orgId != null && "EUR".equals(normalize(deviseCible))) {
                try {
                    return tauxChangeService.tauxVersEur(orgId, deviseBase, LocalDate.now());
                } catch (BusinessException manual) {
                    // Don't mislead the UI for currencies normally provided by the API (USD/GBP/CHF...).
                    log.warn(
                            "FX fallback manual failed for {}->{} (orgId={}, date=today). Original error={}, manual error={}",
                            deviseBase,
                            deviseCible,
                            orgId,
                            be.getCode(),
                            manual.getCode());
                    throw be;
                }
            }
            throw be;
        }
    }

    @Transactional
    public BigDecimal getTauxALaDate(String deviseBase, String deviseCible, LocalDate date) {
        if (date == null) throw BusinessException.badRequest("DATE_TAUX_INVALIDE");
        LocalDate today = LocalDate.now();
        // Frankfurter doesn't provide future rates; fallback to today.
        final LocalDate effectiveDate = date.isAfter(today) ? today : date;
        String base = normalize(deviseBase);
        String cible = normalize(deviseCible);
        if (base.equals(cible)) return BigDecimal.ONE;

        return historiqueRepository
                .findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc(base, cible, effectiveDate)
                .map(TauxChangeHistorique::getTaux)
                .orElseGet(() -> {
                    try {
                        BigDecimal rate = fetchRateForDate(base, cible, effectiveDate.toString());
                        persist(base, cible, effectiveDate, rate, "API");
                        return rate;
                    } catch (Exception ex) {
                        // fallback: nearest older rate
                        BigDecimal fallback =
                                historiqueRepository
                                .findTopByDeviseBaseAndDeviseCibleAndDateTauxLessThanEqualOrderByDateTauxDescCreatedAtDesc(
                                        base, cible, effectiveDate)
                                .map(TauxChangeHistorique::getTaux)
                                .orElse(null);
                        if (fallback != null) {
                            return fallback;
                        }
                        // last resort: try latest (if API transient error on dated endpoint)
                        try {
                            return getTauxDuJour(base, cible);
                        } catch (Exception ignored) {
                            throw new BusinessException(
                                    "TAUX_CHANGE_INDISPONIBLE",
                                    HttpStatus.SERVICE_UNAVAILABLE,
                                    "Impossible de récupérer le taux de change (API indisponible et aucun historique en base).");
                        }
                    }
                });
    }

    @Transactional
    public BigDecimal getTauxALaDate(UUID orgId, String deviseBase, String deviseCible, LocalDate date) {
        try {
            return getTauxALaDate(deviseBase, deviseCible, date);
        } catch (BusinessException be) {
            if (orgId != null && "EUR".equals(normalize(deviseCible))) {
                try {
                    return tauxChangeService.tauxVersEur(orgId, deviseBase, date);
                } catch (BusinessException manual) {
                    log.warn(
                            "FX fallback manual failed for {}->{} (orgId={}, date={}). Original error={}, manual error={}",
                            deviseBase,
                            deviseCible,
                            orgId,
                            date,
                            be.getCode(),
                            manual.getCode());
                    throw be;
                }
            }
            throw be;
        }
    }

    @Cacheable(cacheNames = "fx_tous_taux_du_jour", key = "#deviseBase")
    @Transactional
    public Map<String, BigDecimal> getTousLesTauxDuJour(String deviseBase) {
        String base = normalize(deviseBase);
        if (DEFAULT_TARGETS.contains(base) && DEFAULT_TARGETS.size() == 1) {
            return Map.of(base, BigDecimal.ONE);
        }

        List<String> targets = new ArrayList<>(DEFAULT_TARGETS);
        if (!targets.contains(base)) {
            targets.add(base);
        }
        JsonNode root;
        try {
            root = httpGetJson("/latest?from=" + base + "&to=" + String.join(",", targets));
        } catch (Exception ex) {
            throw new BusinessException(
                    "TAUX_CHANGE_INDISPONIBLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Impossible de récupérer le taux de change (API indisponible).");
        }
        JsonNode ratesNode = root.path("rates");
        if (ratesNode.isMissingNode() || ratesNode.isNull() || !ratesNode.isObject()) {
            throw BusinessException.badRequest("TAUX_CHANGE_INDISPONIBLE");
        }

        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        out.put(base, BigDecimal.ONE);
        ratesNode
                .fields()
                .forEachRemaining(
                        e -> {
                            String cible = normalize(e.getKey());
                            BigDecimal rate =
                                    new BigDecimal(e.getValue().asText()).setScale(6, RoundingMode.HALF_UP);
            out.put(cible, rate);
            persistIfAbsent(base, cible, today, rate, "API");
                        });
        return out;
    }

    @Transactional(readOnly = true)
    public BigDecimal convertir(BigDecimal montant, String deviseSource, String deviseCible, LocalDate date) {
        if (montant == null) throw BusinessException.badRequest("MONTANT_INVALIDE");
        String src = normalize(deviseSource);
        String dst = normalize(deviseCible);
        if (src.equals(dst)) return montant;
        BigDecimal taux = (date == null) ? getTauxDuJour(src, dst) : getTauxALaDate(src, dst, date);
        return montant.multiply(taux).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BigDecimal convertir(UUID orgId, BigDecimal montant, String deviseSource, String deviseCible, LocalDate date) {
        if (montant == null) throw BusinessException.badRequest("MONTANT_INVALIDE");
        String src = normalize(deviseSource);
        String dst = normalize(deviseCible);
        if (src.equals(dst)) return montant;

        // If converting to EUR, we can fallback to manual org rate.
        if ("EUR".equals(dst)) {
            BigDecimal taux = (date == null) ? getTauxDuJour(orgId, src, "EUR") : getTauxALaDate(orgId, src, "EUR", date);
            return montant.multiply(taux).setScale(2, RoundingMode.HALF_UP);
        }

        // Convert via EUR pivot (src->EUR then EUR->dst).
        BigDecimal eur = convertir(orgId, montant, src, "EUR", date);
        BigDecimal tauxEurToDst = (date == null) ? getTauxDuJour("EUR", dst) : getTauxALaDate("EUR", dst, date);
        return eur.multiply(tauxEurToDst).setScale(2, RoundingMode.HALF_UP);
    }

    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void refreshTauxQuotidien() {
        // Global refresh (no org config yet) for the default target set, base EUR.
        try {
            getTousLesTauxDuJour("EUR");
        } catch (Exception ignored) {
            // best-effort refresh
        }
    }

    private BigDecimal fetchRateForDate(String base, String cible, String dateOrLatest) {
        String path = "/" + dateOrLatest + "?from=" + base + "&to=" + cible;
        try {
            JsonNode root = httpGetJson(path);
            JsonNode v = root.path("rates").path(cible);
            if (!v.isMissingNode() && !v.isNull()) {
                return new BigDecimal(v.asText()).setScale(6, RoundingMode.HALF_UP);
            }
            String snippet = root.toString().replaceAll("\\s+", " ");
            if (snippet.length() > 300) snippet = snippet.substring(0, 300) + "...";
            log.warn("FX JSON without rate for {}->{} at {}. jsonSnippet={}", base, cible, dateOrLatest, snippet);
        } catch (Exception ex) {
            log.warn(
                    "FX fetch exception for {}->{} at {}: {}: {}",
                    base,
                    cible,
                    dateOrLatest,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }

        log.warn("FX fetch failed for {}->{} at {} (no rate in response)", base, cible, dateOrLatest);
        throw BusinessException.badRequest("TAUX_CHANGE_INDISPONIBLE");
    }

    private JsonNode httpGetJson(String pathWithQuery) throws Exception {
        URI uri = URI.create(BASE_URL + pathWithQuery);

        HttpRequest req =
                HttpRequest.newBuilder().uri(uri).header("Accept", "application/json").GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        // If we still get redirected for some reason, expose Location for debugging.
        if (resp.statusCode() >= 300 && resp.statusCode() < 400) {
            String location = resp.headers().firstValue("location").orElse("");
            log.warn("FX redirect {} -> {} (status={})", uri, location, resp.statusCode());
        }

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String raw = resp.body();
            String snippet = raw == null ? "" : raw.replaceAll("\\s+", " ");
            if (snippet.length() > 300) snippet = snippet.substring(0, 300) + "...";
            throw new BusinessException(
                    "TAUX_CHANGE_INDISPONIBLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Frankfurter non-2xx: " + resp.statusCode() + " body=" + snippet);
        }

        String raw = resp.body();
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(
                    "TAUX_CHANGE_INDISPONIBLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Frankfurter empty body");
        }
        return objectMapper.readTree(raw);
    }

    private void persistIfAbsent(String base, String cible, LocalDate date, BigDecimal taux, String source) {
        historiqueRepository
                .findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc(base, cible, date)
                .ifPresentOrElse(
                        __ -> {},
                        () -> persist(base, cible, date, taux, source));
    }

    private void persist(String base, String cible, LocalDate date, BigDecimal taux, String source) {
        TauxChangeHistorique t = new TauxChangeHistorique();
        t.setDeviseBase(base);
        t.setDeviseCible(cible);
        t.setDateTaux(date);
        t.setTaux(taux);
        t.setSource(source == null ? "API" : source);
        t.setCreatedAt(Instant.now());
        historiqueRepository.save(t);
    }

    private static String normalize(String devise) {
        String d = devise == null ? "EUR" : devise.trim().toUpperCase(Locale.ROOT);
        return d.isBlank() ? "EUR" : d;
    }
}

