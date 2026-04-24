package com.app.audit;

import com.app.audit.entity.AuditLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private AuditLogService auditLogService;

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void log_sauveAvecJsonEtSansEnrichissementQuandPasDeContexteHttp() {
        UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");
        UUID entityId = UUID.fromString("c0000000-0000-0000-0000-000000000001");

        var avant = Map.of("k", "v1");
        var apres = Map.of("k", "v2");

        var jsonAvant = new ObjectMapper().valueToTree(avant);
        var jsonApres = new ObjectMapper().valueToTree(apres);

        // On force des JsonNodes “connus” pour vérifier que valueToTree est bien appelé
        org.mockito.Mockito.when(objectMapper.valueToTree(avant)).thenReturn(jsonAvant);
        org.mockito.Mockito.when(objectMapper.valueToTree(apres)).thenReturn(jsonApres);

        auditLogService.log(orgId, userId, "UPDATE", "Facture", entityId, avant, apres);

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        AuditLog saved = cap.getValue();

        assertThat(saved.getOrganisationId()).isEqualTo(orgId);
        assertThat(saved.getUtilisateurId()).isEqualTo(userId);
        assertThat(saved.getAction()).isEqualTo("UPDATE");
        assertThat(saved.getEntite()).isEqualTo("Facture");
        assertThat(saved.getEntiteId()).isEqualTo(entityId);
        assertThat(saved.getAvant()).isEqualTo(jsonAvant);
        assertThat(saved.getApres()).isEqualTo(jsonApres);
        assertThat(saved.getIpAddress()).isNull();
        assertThat(saved.getUserAgent()).isNull();
        assertThat(saved.getDateAction()).isNotNull();
    }

    @Test
    void log_enrichitIpEtUserAgentDepuisRequeteHttp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        req.addHeader("User-Agent", "JUnit");
        req.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        UUID orgId = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("b1000000-0000-0000-0000-000000000001");

        org.mockito.Mockito.when(objectMapper.valueToTree(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ObjectMapper().createObjectNode());

        auditLogService.log(orgId, userId, "CREATE", "Paiement", null, Map.of("x", 1), Map.of("x", 2));

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        AuditLog saved = cap.getValue();

        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(saved.getUserAgent()).isEqualTo("JUnit");
    }
}

