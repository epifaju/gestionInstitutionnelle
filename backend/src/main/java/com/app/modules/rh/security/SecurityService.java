package com.app.modules.rh.security;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rh.repository.SalarieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final SalarieRepository salarieRepository;

    public boolean isSelf(UUID salarieId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails ud)) {
            return false;
        }
        return salarieRepository
                .findById(salarieId)
                .map(s -> s.getUtilisateurId() != null && s.getUtilisateurId().equals(ud.getId()))
                .orElse(false);
    }
}
