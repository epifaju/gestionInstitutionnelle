package com.app.modules.payroll.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.payroll.dto.EmployeePayrollProfileRequest;
import com.app.modules.payroll.dto.EmployeePayrollProfileResponse;
import com.app.modules.payroll.dto.PayrollEmployerSettingsRequest;
import com.app.modules.payroll.dto.PayrollEmployerSettingsResponse;
import com.app.modules.payroll.dto.PayrollLegalConstantRequest;
import com.app.modules.payroll.dto.PayrollLegalConstantResponse;
import com.app.modules.payroll.dto.PayrollRubriqueRequest;
import com.app.modules.payroll.dto.PayrollRubriqueResponse;
import com.app.modules.payroll.service.PayrollAdminService;
import com.app.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PayrollAdminControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PayrollAdminService payrollAdminService = mock(PayrollAdminService.class);
    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID CONST_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PayrollAdminController(payrollAdminService))
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private void withAuth(ThrowingRunnable r) throws Exception {
        Organisation o = new Organisation();
        o.setId(ORG_ID);
        Utilisateur u = new Utilisateur();
        u.setId(USER_ID);
        u.setOrganisationId(ORG_ID);
        u.setOrganisation(o);
        u.setEmail("rh@test.com");
        u.setRole(Role.RH);
        CustomUserDetails cud = new CustomUserDetails(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities()));
        try {
            r.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    void getEmployer_ok() throws Exception {
        when(payrollAdminService.getEmployerSettings(ORG_ID)).thenReturn(new PayrollEmployerSettingsResponse(
                ORG_ID, "ACME", "L1", null, "75000", "Paris", "FR", null, null, null, null, null));
        withAuth(() -> mockMvc.perform(get("/api/v1/admin/payroll/employer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.raisonSociale").value("ACME")));
    }

    @Test
    void upsertEmployer_ok() throws Exception {
        PayrollEmployerSettingsRequest req = new PayrollEmployerSettingsRequest(
                "ACME", "L1", null, "75000", "Paris", "FR", "SIRET", "NAF", "URSSAF", "CC", "Conv");
        when(payrollAdminService.upsertEmployerSettings(eq(ORG_ID), eq(USER_ID), any())).thenReturn(
                new PayrollEmployerSettingsResponse(ORG_ID, "ACME", "L1", null, "75000", "Paris", "FR", "SIRET", "NAF", "URSSAF", "CC", "Conv"));
        withAuth(() -> mockMvc.perform(put("/api/v1/admin/payroll/employer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siret").value("SIRET")));
    }

    @Test
    void constants_listCreateDelete() throws Exception {
        when(payrollAdminService.listLegalConstants(ORG_ID)).thenReturn(List.of(
                new PayrollLegalConstantResponse(CONST_ID, "PMSS", "Plafond", BigDecimal.TEN, LocalDate.of(2026, 1, 1), null)));
        when(payrollAdminService.createLegalConstant(eq(ORG_ID), eq(USER_ID), any())).thenReturn(
                new PayrollLegalConstantResponse(CONST_ID, "PMSS", "Plafond", BigDecimal.TEN, LocalDate.of(2026, 1, 1), null));

        withAuth(() -> {
            mockMvc.perform(get("/api/v1/admin/payroll/constants"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].code").value("PMSS"));
            mockMvc.perform(post("/api/v1/admin/payroll/constants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new PayrollLegalConstantRequest(
                                    "PMSS", "Plafond", BigDecimal.TEN, LocalDate.of(2026, 1, 1), null))))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/v1/admin/payroll/constants/{id}", CONST_ID))
                    .andExpect(status().isOk());
        });
        verify(payrollAdminService).deleteLegalConstant(CONST_ID, ORG_ID, USER_ID);
    }

    @Test
    void rubriques_listCreate() throws Exception {
        when(payrollAdminService.listRubriques(ORG_ID)).thenReturn(List.of(
                new PayrollRubriqueResponse(CONST_ID, "PRIME", "Prime", "GAIN", "FIXED", null,
                        null, null, BigDecimal.TEN, 10, true, LocalDate.of(2026, 1, 1), null)));
        when(payrollAdminService.createRubrique(eq(ORG_ID), eq(USER_ID), any())).thenReturn(
                new PayrollRubriqueResponse(CONST_ID, "PRIME", "Prime", "GAIN", "FIXED", null,
                        null, null, BigDecimal.TEN, 10, true, LocalDate.of(2026, 1, 1), null));

        withAuth(() -> {
            mockMvc.perform(get("/api/v1/admin/payroll/rubriques"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].code").value("PRIME"));
            mockMvc.perform(post("/api/v1/admin/payroll/rubriques")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new PayrollRubriqueRequest(
                                    "PRIME", "Prime", "GAIN", "FIXED", null, null, null, BigDecimal.TEN, 10, true,
                                    LocalDate.of(2026, 1, 1), null))))
                    .andExpect(status().isOk());
        });
    }

    @Test
    void profiles_listUpsert() throws Exception {
        UUID salarieId = UUID.fromString("d0000000-0000-0000-0000-000000000001");
        when(payrollAdminService.listEmployeeProfiles(ORG_ID)).thenReturn(List.of(
                new EmployeePayrollProfileResponse(CONST_ID, salarieId, "Dupont Alice", true, "C", "L", null)));
        when(payrollAdminService.upsertEmployeeProfile(eq(ORG_ID), eq(USER_ID), any())).thenReturn(
                new EmployeePayrollProfileResponse(CONST_ID, salarieId, "Dupont Alice", true, "C", "L", null));

        withAuth(() -> {
            mockMvc.perform(get("/api/v1/admin/payroll/profiles"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].cadre").value(true));
            mockMvc.perform(put("/api/v1/admin/payroll/profiles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new EmployeePayrollProfileRequest(salarieId, true, "C", "L", null))))
                    .andExpect(status().isOk());
        });
    }
}
