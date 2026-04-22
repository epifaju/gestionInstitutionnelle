package com.app.modules.finance.service;

import com.app.modules.finance.entity.TauxChange;
import com.app.modules.finance.repository.TauxChangeRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TauxChangeService {

    private final TauxChangeRepository tauxChangeRepository;

    public static final List<String> DEVISES_SUPPORT = List.of("EUR", "XOF", "USD");

    @Transactional(readOnly = true)
    public BigDecimal tauxVersEur(UUID orgId, String devise, LocalDate date) {
        String d = normalizeDevise(devise);
        if ("EUR".equals(d)) {
            return BigDecimal.ONE;
        }
        if (date == null) {
            throw BusinessException.badRequest("DATE_TAUX_INVALIDE");
        }
        return tauxChangeRepository
                .findByOrganisationIdAndDateAndDevise(orgId, date, d)
                .map(TauxChange::getTauxVersEur)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "TAUX_CHANGE_ABSENT",
                                        HttpStatus.BAD_REQUEST,
                                        "Taux de change manquant pour "
                                                + d
                                                + " à la date "
                                                + date
                                                + ". Renseignez-le dans Finance → Taux de change."));
    }

    @Transactional(readOnly = true)
    public List<TauxChange> listForDate(UUID orgId, LocalDate date) {
        if (date == null) {
            throw BusinessException.badRequest("DATE_TAUX_INVALIDE");
        }
        return tauxChangeRepository.findAllByOrganisationIdAndDate(orgId, date);
    }

    @Transactional
    public TauxChange upsert(UUID orgId, LocalDate date, String devise, BigDecimal tauxVersEur) {
        String d = normalizeDevise(devise);
        if (!DEVISES_SUPPORT.contains(d)) {
            throw BusinessException.badRequest("DEVISE_INVALIDE");
        }
        if (date == null) {
            throw BusinessException.badRequest("DATE_TAUX_INVALIDE");
        }
        if (tauxVersEur == null || tauxVersEur.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.badRequest("TAUX_CHANGE_INVALIDE");
        }
        if ("EUR".equals(d)) {
            // Toujours 1 — on autorise l'upsert mais on force la valeur.
            tauxVersEur = BigDecimal.ONE;
        }

        Instant now = Instant.now();
        TauxChange tc =
                tauxChangeRepository
                        .findByOrganisationIdAndDateAndDevise(orgId, date, d)
                        .orElseGet(() -> {
                            TauxChange x = new TauxChange();
                            x.setOrganisationId(orgId);
                            x.setDate(date);
                            x.setDevise(d);
                            x.setCreatedAt(now);
                            return x;
                        });
        tc.setTauxVersEur(tauxVersEur);
        tc.setUpdatedAt(now);
        return tauxChangeRepository.save(tc);
    }

    private static String normalizeDevise(String devise) {
        String d = devise == null ? "EUR" : devise.trim().toUpperCase(Locale.ROOT);
        if (d.isBlank()) {
            d = "EUR";
        }
        return d;
    }
}
