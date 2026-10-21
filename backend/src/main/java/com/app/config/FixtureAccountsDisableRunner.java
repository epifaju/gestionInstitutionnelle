package com.app.config;

import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Désactive les comptes Flyway/E2E connus ({@code *@test.com}) en profil {@code prod}.
 * Les migrations historiques restent inchangées pour le dev et les E2E.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class FixtureAccountsDisableRunner implements ApplicationRunner {

    static final List<String> FIXTURE_EMAILS = List.of(
            "admin@test.com",
            "rh@test.com",
            "financier@test.com",
            "employe@test.com");

    private final UtilisateurRepository utilisateurRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Utilisateur> fixtures = utilisateurRepository.findByEmailIgnoreCaseIn(FIXTURE_EMAILS);
        int disabled = 0;
        for (Utilisateur u : fixtures) {
            if (u.isActif()) {
                u.setActif(false);
                disabled++;
            }
        }
        if (disabled > 0) {
            utilisateurRepository.saveAll(fixtures);
            log.warn(
                    "Profil prod : {} compte(s) fixture désactivé(s) ({}) — ne pas utiliser AdminTest123! en production",
                    disabled,
                    FIXTURE_EMAILS);
        } else if (!fixtures.isEmpty()) {
            log.info("Profil prod : {} compte(s) fixture déjà inactif(s)", fixtures.size());
        }
    }
}
