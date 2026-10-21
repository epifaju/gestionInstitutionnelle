package com.app.config;

import com.app.modules.auth.entity.Utilisateur;
import com.app.modules.auth.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixtureAccountsDisableRunnerTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private FixtureAccountsDisableRunner runner;

    @Test
    void desactiveComptesActifs() {
        Utilisateur admin = new Utilisateur();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@test.com");
        admin.setActif(true);
        Utilisateur alreadyOff = new Utilisateur();
        alreadyOff.setId(UUID.randomUUID());
        alreadyOff.setEmail("rh@test.com");
        alreadyOff.setActif(false);

        when(utilisateurRepository.findByEmailIgnoreCaseIn(FixtureAccountsDisableRunner.FIXTURE_EMAILS))
                .thenReturn(List.of(admin, alreadyOff));

        runner.run(new DefaultApplicationArguments());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Utilisateur>> cap = ArgumentCaptor.forClass(List.class);
        verify(utilisateurRepository).saveAll(cap.capture());
        assertThat(admin.isActif()).isFalse();
        assertThat(cap.getValue()).containsExactly(admin, alreadyOff);
    }

    @Test
    void neSauvegardePasSiAucunActif() {
        Utilisateur u = new Utilisateur();
        u.setEmail("admin@test.com");
        u.setActif(false);
        when(utilisateurRepository.findByEmailIgnoreCaseIn(FixtureAccountsDisableRunner.FIXTURE_EMAILS))
                .thenReturn(List.of(u));

        runner.run(new DefaultApplicationArguments());

        verify(utilisateurRepository).findByEmailIgnoreCaseIn(FixtureAccountsDisableRunner.FIXTURE_EMAILS);
        verify(utilisateurRepository, never()).saveAll(any());
    }
}
