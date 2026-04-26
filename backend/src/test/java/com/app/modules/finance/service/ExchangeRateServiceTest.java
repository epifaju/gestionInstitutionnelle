package com.app.modules.finance.service;

import com.app.modules.finance.entity.TauxChangeHistorique;
import com.app.modules.finance.repository.TauxChangeHistoriqueRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock TauxChangeHistoriqueRepository historiqueRepository;
    @Mock TauxChangeService tauxChangeService;

    @InjectMocks ExchangeRateService service;

    @Test
    void getTauxALaDate_returnsOne_whenSameCurrency() {
        BigDecimal out = service.getTauxALaDate("EUR", "EUR", LocalDate.of(2026, 4, 24));
        assertThat(out).isEqualByComparingTo(BigDecimal.ONE);
        verify(historiqueRepository, never()).findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void getTauxALaDate_usesHistoriqueExactDate_whenPresent() {
        LocalDate date = LocalDate.of(2026, 4, 24);
        TauxChangeHistorique h = new TauxChangeHistorique();
        h.setTaux(new BigDecimal("0.923456"));

        when(historiqueRepository.findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc("USD", "EUR", date))
                .thenReturn(Optional.of(h));

        BigDecimal out = service.getTauxALaDate("USD", "EUR", date);
        assertThat(out).isEqualByComparingTo("0.923456");
        verify(historiqueRepository, never())
                .findTopByDeviseBaseAndDeviseCibleAndDateTauxLessThanEqualOrderByDateTauxDescCreatedAtDesc(any(), any(), any());
    }

    @Test
    void getTauxALaDate_orgOverload_delegatesToCoreWhenOk() {
        LocalDate date = LocalDate.of(2026, 4, 24);
        UUID orgId = UUID.randomUUID();
        TauxChangeHistorique h = new TauxChangeHistorique();
        h.setTaux(new BigDecimal("1.250000"));

        when(historiqueRepository.findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc("GBP", "EUR", date))
                .thenReturn(Optional.of(h));

        BigDecimal out = service.getTauxALaDate(orgId, "GBP", "EUR", date);
        assertThat(out).isEqualByComparingTo("1.250000");
        verify(tauxChangeService, never()).tauxVersEur(eq(orgId), any(), eq(date));
    }
}

