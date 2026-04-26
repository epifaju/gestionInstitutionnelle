package com.app.modules.finance.service;

import com.app.modules.finance.entity.TauxChange;
import com.app.modules.finance.repository.TauxChangeRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TauxChangeServiceTest {

    @Mock private TauxChangeRepository tauxChangeRepository;
    @InjectMocks private TauxChangeService tauxChangeService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Test
    void tauxVersEur_EUR_retourne1_sansRepo() {
        assertThat(tauxChangeService.tauxVersEur(orgId, "EUR", LocalDate.now())).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void tauxVersEur_dateNull_refuse() {
        assertThatThrownBy(() -> tauxChangeService.tauxVersEur(orgId, "USD", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DATE_TAUX_INVALIDE");
    }

    @Test
    void tauxVersEur_absent_refuse() {
        LocalDate d = LocalDate.of(2026, 4, 22);
        when(tauxChangeRepository.findByOrganisationIdAndDateAndDevise(orgId, d, "USD")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tauxChangeService.tauxVersEur(orgId, "usd", d))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TAUX_CHANGE_ABSENT");
    }

    @Test
    void upsert_deviseInvalide_refuse() {
        assertThatThrownBy(() -> tauxChangeService.upsert(orgId, LocalDate.now(), "ZZZ", new BigDecimal("1.2")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DEVISE_INVALIDE");
    }

    @Test
    void upsert_tauxNonPositif_refuse() {
        assertThatThrownBy(() -> tauxChangeService.upsert(orgId, LocalDate.now(), "USD", BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TAUX_CHANGE_INVALIDE");
    }

    @Test
    void upsert_EUR_force1() {
        LocalDate d = LocalDate.of(2026, 4, 22);
        when(tauxChangeRepository.findByOrganisationIdAndDateAndDevise(orgId, d, "EUR")).thenReturn(Optional.empty());
        when(tauxChangeRepository.save(any(TauxChange.class))).thenAnswer(inv -> inv.getArgument(0));

        TauxChange tc = tauxChangeService.upsert(orgId, d, "eur", new BigDecimal("2.5"));

        assertThat(tc.getDevise()).isEqualTo("EUR");
        assertThat(tc.getTauxVersEur()).isEqualByComparingTo(BigDecimal.ONE);
        verify(tauxChangeRepository).save(any(TauxChange.class));
    }

    @Test
    void upsert_existant_metAJour() {
        LocalDate d = LocalDate.of(2026, 4, 22);
        TauxChange existing = new TauxChange();
        existing.setOrganisationId(orgId);
        existing.setDate(d);
        existing.setDevise("USD");
        existing.setTauxVersEur(new BigDecimal("0.8"));
        when(tauxChangeRepository.findByOrganisationIdAndDateAndDevise(orgId, d, "USD")).thenReturn(Optional.of(existing));
        when(tauxChangeRepository.save(any(TauxChange.class))).thenAnswer(inv -> inv.getArgument(0));

        TauxChange tc = tauxChangeService.upsert(orgId, d, "USD", new BigDecimal("0.9"));

        assertThat(tc.getTauxVersEur()).isEqualByComparingTo(new BigDecimal("0.9"));
        verify(tauxChangeRepository).save(eq(existing));
    }
}

