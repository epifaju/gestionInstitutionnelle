package com.app.modules.ged.controller;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.entity.Role;
import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.ged.dto.DocumentResponse;
import com.app.modules.ged.dto.DocumentShareRequest;
import com.app.modules.ged.dto.DocumentUpdateRequest;
import com.app.modules.ged.dto.DocumentUploadRequest;
import com.app.modules.ged.service.DocumentService;
import com.app.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final DocumentService documentService = mock(DocumentService.class);

    private MockMvc mockMvc;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID DOC_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var env = mock(org.springframework.core.env.Environment.class);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new DocumentController(documentService))
                        .setControllerAdvice(new GlobalExceptionHandler(env))
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .setValidator(validator)
                        .setCustomArgumentResolvers(
                                new AuthenticationPrincipalArgumentResolver(),
                                new PageableHandlerMethodArgumentResolver())
                        .build();
    }

    private UsernamePasswordAuthenticationToken auth(Role role) {
        Organisation o = new Organisation();
        o.setId(ORG_ID);
        o.setNom("Org");
        Utilisateur u = new Utilisateur();
        u.setId(USER_ID);
        u.setOrganisationId(ORG_ID);
        u.setOrganisation(o);
        u.setEmail("user@test.com");
        u.setRole(role);
        CustomUserDetails cud = new CustomUserDetails(u);
        return new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities());
    }

    private void withAuth(UsernamePasswordAuthenticationToken auth, ThrowingRunnable r) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(auth);
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

    private static DocumentResponse sampleDoc() {
        return new DocumentResponse(
                DOC_ID,
                "Titre",
                null,
                "FACTURE",
                null,
                "doc.pdf",
                10L,
                "application/pdf",
                1,
                null,
                "ORGANISATION",
                null,
                null,
                null,
                null,
                "Doe John",
                LocalDateTime.of(2026, 1, 15, 10, 0),
                null);
    }

    @Test
    void search_ok_retournePage() throws Exception {
        when(documentService.search(eq(ORG_ID), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleDoc()), PageRequest.of(0, 20), 1));

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(get("/api/v1/documents").param("query", "facture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].titre").value("Titre")));
    }

    @Test
    void getById_ok() throws Exception {
        when(documentService.getById(DOC_ID, ORG_ID, USER_ID)).thenReturn(sampleDoc());

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(get("/api/v1/documents/{id}", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(DOC_ID.toString()))
                .andExpect(jsonPath("$.data.typeDocument").value("FACTURE")));
    }

    @Test
    void getPresignedUrl_ok() throws Exception {
        when(documentService.generatePresignedUrl(DOC_ID, ORG_ID, USER_ID)).thenReturn("https://signed");

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(get("/api/v1/documents/{id}/url", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://signed")));
    }

    @Test
    void upload_ok() throws Exception {
        when(documentService.upload(any(DocumentUploadRequest.class), any(), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(sampleDoc());

        DocumentUploadRequest req = new DocumentUploadRequest(
                "Titre", null, "FACTURE", null, null, null, null, null, null, null);
        MockMultipartFile meta =
                new MockMultipartFile(
                        "document",
                        "",
                        MediaType.APPLICATION_JSON_VALUE,
                        objectMapper.writeValueAsBytes(req));
        MockMultipartFile file =
                new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.4\n".getBytes());

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(multipart("/api/v1/documents").file(meta).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.titre").value("Titre")));

        verify(documentService).upload(any(DocumentUploadRequest.class), any(), eq(ORG_ID), eq(USER_ID));
    }

    @Test
    void modifier_ok() throws Exception {
        when(documentService.modifier(eq(DOC_ID), any(DocumentUpdateRequest.class), eq(ORG_ID), eq(USER_ID)))
                .thenReturn(sampleDoc());

        DocumentUpdateRequest req =
                new DocumentUpdateRequest("Titre", null, null, null, null, null, null, null);

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(put("/api/v1/documents/{id}", DOC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));
    }

    @Test
    void supprimer_ok() throws Exception {
        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(delete("/api/v1/documents/{id}", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)));

        verify(documentService).supprimer(DOC_ID, ORG_ID, USER_ID);
    }

    @Test
    void versions_ok() throws Exception {
        when(documentService.getVersions(DOC_ID, ORG_ID, USER_ID)).thenReturn(List.of(sampleDoc()));

        withAuth(auth(Role.EMPLOYE), () -> mockMvc.perform(get("/api/v1/documents/{id}/versions", DOC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].version").value(1)));
    }

    @Test
    void partager_ok() throws Exception {
        UUID cible = UUID.fromString("d0000000-0000-0000-0000-000000000001");
        DocumentShareRequest req = new DocumentShareRequest(cible, true, false);

        withAuth(auth(Role.ADMIN), () -> mockMvc.perform(post("/api/v1/documents/{id}/partager", DOC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()));

        verify(documentService).partagerAvec(DOC_ID, cible, true, false, ORG_ID);
    }

    @Test
    void expirationProchaine_ok() throws Exception {
        when(documentService.getDocumentsExpirantBientot(eq(ORG_ID), anyInt()))
                .thenReturn(List.of(sampleDoc()));

        withAuth(auth(Role.RH), () -> mockMvc.perform(get("/api/v1/documents/expiration-prochaine").param("nbJours", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(DOC_ID.toString())));

        verify(documentService).getDocumentsExpirantBientot(ORG_ID, 15);
    }
}
