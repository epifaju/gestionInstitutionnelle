package com.app.modules.finance.service;

import com.app.modules.finance.dto.CategorieMontantDto;
import com.app.modules.finance.dto.StatsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private StatsService statsService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Test
    @SuppressWarnings({"unchecked"})
    void getStatsMensuelles_calculSolde_etDefaultCounts() {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(orgId), eq(2026), eq(4)))
                .thenReturn(new BigDecimal("50.00"))   // depenses
                .thenReturn(new BigDecimal("120.00")); // recettes

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(orgId), eq(2026), eq(4)))
                .thenReturn(null) // nbFactures
                .thenReturn(2L);  // nbFacturesEnAttente

        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(orgId), eq(2026), eq(4)))
                .thenReturn(List.of(new CategorieMontantDto("Sans catégorie", new BigDecimal("50.00"))))
                .thenReturn(List.of(new CategorieMontantDto("Sans catégorie", new BigDecimal("120.00"))));

        StatsResponse res = statsService.getStatsMensuelles(orgId, 2026, 4);

        assertThat(res.totalDepenses()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(res.totalRecettes()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(res.solde()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(res.nbFactures()).isEqualTo(0L);
        assertThat(res.nbFacturesEnAttente()).isEqualTo(2L);
        assertThat(res.devise()).isEqualTo("EUR");
    }
}

