package com.app.modules.finance.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FactureSequenceServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private FactureSequenceService factureSequenceService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Test
    @SuppressWarnings({"unchecked"})
    void nextSequence_siUpdateRetourneValeur_retourneValeur() {
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(orgId), eq(2026)))
                .thenReturn(List.of(7));

        int seq = factureSequenceService.nextSequence(orgId, 2026);
        assertThat(seq).isEqualTo(7);
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void nextSequence_siUpdateVide_puisRetryOk() {
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(orgId), eq(2026)))
                .thenReturn(List.of())
                .thenReturn(List.of(1));

        int seq = factureSequenceService.nextSequence(orgId, 2026);
        assertThat(seq).isEqualTo(1);
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void nextSequence_siUpdateVide_etRetryVide_lanceIllegalState() {
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(orgId), eq(2026)))
                .thenReturn(List.of())
                .thenReturn(List.of());

        assertThatThrownBy(() -> factureSequenceService.nextSequence(orgId, 2026))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("séquence facture");
    }
}

