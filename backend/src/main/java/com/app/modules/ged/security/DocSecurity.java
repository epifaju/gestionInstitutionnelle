package com.app.modules.ged.security;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.ged.entity.Document;
import com.app.modules.ged.repository.DocumentAccesRepository;
import com.app.modules.ged.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("docSecurity")
@RequiredArgsConstructor
public class DocSecurity {

    private final DocumentRepository documentRepository;
    private final DocumentAccesRepository documentAccesRepository;

    public boolean canDelete(UUID id, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails ud)) {
            return false;
        }
        UUID orgId = ud.getOrganisationId();
        UUID userId = ud.getId();

        Document d = documentRepository.findById(id).orElse(null);
        if (d == null || d.isSupprime() || !orgId.equals(d.getOrganisationId())) {
            return false;
        }

        // ADMIN can delete any document in org (controller still enforces role OR this bean).
        boolean isAdmin = ud.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) return true;

        if (userId != null && userId.equals(d.getUploadePar())) {
            return true;
        }

        return documentAccesRepository
                .findById_DocumentIdAndId_UtilisateurId(d.getId(), userId)
                .map(acc -> acc.isPeutSupprimer())
                .orElse(false);
    }
}

