package com.app.modules.finance.service;

import com.app.modules.finance.entity.TauxChangeHistorique;
import com.app.modules.finance.repository.TauxChangeHistoriqueRepository;
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

    @Test
    void getTauxDuJour_sameCurrency_returnsOne() {
        assertThat(service.getTauxDuJour("eur", "EUR")).isEqualByComparingTo(BigDecimal.ONE);
        verify(historiqueRepository, never())
                .findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void getTauxDuJour_fromHistoriqueToday() {
        LocalDate today = LocalDate.now();
        TauxChangeHistorique h = new TauxChangeHistorique();
        h.setTaux(new BigDecimal("0.910000"));
        when(historiqueRepository.findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc("USD", "EUR", today))
                .thenReturn(Optional.of(h));

        assertThat(service.getTauxDuJour("USD", "EUR")).isEqualByComparingTo("0.910000");
        verify(historiqueRepository, never()).save(any());
    }

    @Test
    void getTauxALaDate_null_refuse() {
        assertThatThrownBy(() -> service.getTauxALaDate("USD", "EUR", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DATE_TAUX_INVALIDE");
    }

    @Test
    void getTauxALaDate_fallbackOlderHistorique_quandApiEchoue() {
        // Devise inventée → Frankfurter échoue ; on vérifie le fallback historique sans dépendre du taux live.
        LocalDate date = LocalDate.of(2026, 3, 1);
        when(historiqueRepository.findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc("ZZZ", "EUR", date))
                .thenReturn(Optional.empty());
        TauxChangeHistorique older = new TauxChangeHistorique();
        older.setTaux(new BigDecimal("0.880000"));
        when(historiqueRepository.findTopByDeviseBaseAndDeviseCibleAndDateTauxLessThanEqualOrderByDateTauxDescCreatedAtDesc(
                        "ZZZ", "EUR", date))
                .thenReturn(Optional.of(older));

        assertThat(service.getTauxALaDate("ZZZ", "EUR", date)).isEqualByComparingTo("0.880000");
    }

    @Test
    void convertir_sameCurrency() {
        assertThat(service.convertir(new BigDecimal("10.00"), "EUR", "EUR", LocalDate.of(2026, 4, 24)))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void convertir_nullMontant() {
        assertThatThrownBy(() -> service.convertir(null, "USD", "EUR", LocalDate.of(2026, 4, 24)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "MONTANT_INVALIDE");
    }

    @Test
    void convertir_utiliseHistorique() {
        LocalDate date = LocalDate.of(2026, 4, 24);
        TauxChangeHistorique h = new TauxChangeHistorique();
        h.setTaux(new BigDecimal("0.900000"));
        when(historiqueRepository.findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc("USD", "EUR", date))
                .thenReturn(Optional.of(h));

        assertThat(service.convertir(new BigDecimal("100.00"), "USD", "EUR", date))
                .isEqualByComparingTo("90.00");
    }

    @Test
    void convertir_orgVersEur() {
        UUID orgId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 4, 24);
        TauxChangeHistorique h = new TauxChangeHistorique();
        h.setTaux(new BigDecimal("1.100000"));
        when(historiqueRepository.findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc("GBP", "EUR", date))
                .thenReturn(Optional.of(h));

        assertThat(service.convertir(orgId, new BigDecimal("50.00"), "GBP", "EUR", date))
                .isEqualByComparingTo("55.00");
    }

    @Test
    void getTauxDuJour_org_fallbackManuelQuandApiEtHistoriqueAbsent() {
        UUID orgId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        // Devise inventée → API échoue ; historique vide → fallback table manuelle org
        when(historiqueRepository.findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc("ZZ1", "EUR", today))
                .thenReturn(Optional.empty());
        when(historiqueRepository.findTopByDeviseBaseAndDeviseCibleAndDateTauxLessThanEqualOrderByDateTauxDescCreatedAtDesc(
                        "ZZ1", "EUR", today))
                .thenReturn(Optional.empty());
        when(tauxChangeService.tauxVersEur(orgId, "ZZ1", today)).thenReturn(new BigDecimal("0.500000"));

        assertThat(service.getTauxDuJour(orgId, "ZZ1", "EUR")).isEqualByComparingTo("0.500000");
    }

    @Test
    void getTauxALaDate_futureDate_utiliseAujourdhui() {
        LocalDate future = LocalDate.now().plusDays(10);
        LocalDate today = LocalDate.now();
        TauxChangeHistorique h = new TauxChangeHistorique();
        h.setTaux(new BigDecimal("0.990000"));
        when(historiqueRepository.findTopByDeviseBaseAndDeviseCibleAndDateTauxOrderByCreatedAtDesc("USD", "EUR", today))
                .thenReturn(Optional.of(h));

        assertThat(service.getTauxALaDate("USD", "EUR", future)).isEqualByComparingTo("0.990000");
    }
}

