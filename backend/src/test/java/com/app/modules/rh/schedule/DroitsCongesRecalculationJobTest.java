package com.app.modules.rh.schedule;

import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.rh.entity.DroitsConges;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutConge;
import com.app.modules.rh.entity.StatutSalarie;
import com.app.modules.rh.entity.TypeConge;
import com.app.modules.rh.repository.CongeRepository;
import com.app.modules.rh.repository.DroitsCongesRepository;
import com.app.modules.rh.repository.SalarieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DroitsCongesRecalculationJobTest {

    @Mock private OrganisationRepository organisationRepository;
    @Mock private SalarieRepository salarieRepository;
    @Mock private DroitsCongesRepository droitsCongesRepository;
    @Mock private CongeRepository congeRepository;

    @InjectMocks private DroitsCongesRecalculationJob job;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID salarieId = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Test
    void recalculerDroitsNouvelAn_creeDroitsPourSalarieActif() {
        int annee = LocalDate.now().getYear();
        Organisation org = new Organisation();
        org.setId(orgId);
        org.setActif(true);

        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        s.setDateEmbauche(LocalDate.of(annee - 2, 1, 15));
        s.setStatut(StatutSalarie.ACTIF);

        when(organisationRepository.findByActifTrue()).thenReturn(List.of(org));
        when(salarieRepository.findByOrganisationIdAndStatut(orgId, StatutSalarie.ACTIF)).thenReturn(List.of(s));
        when(congeRepository.sumJoursValidesSoldeEntreDates(
                        eq(salarieId),
                        eq(StatutConge.VALIDE),
                        eq(TypeConge.ANNUEL),
                        eq(TypeConge.EXCEPTIONNEL),
                        eq(LocalDate.of(annee, 1, 1)),
                        eq(LocalDate.of(annee, 12, 31))))
                .thenReturn(new BigDecimal("5.0"));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(salarieId, annee)).thenReturn(Optional.empty());
        when(droitsCongesRepository.save(any(DroitsConges.class))).thenAnswer(inv -> inv.getArgument(0));

        job.recalculerDroitsNouvelAn();

        ArgumentCaptor<DroitsConges> cap = ArgumentCaptor.forClass(DroitsConges.class);
        verify(droitsCongesRepository).save(cap.capture());
        DroitsConges saved = cap.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(orgId);
        assertThat(saved.getAnnee()).isEqualTo(annee);
        // 12 mois × 2,5 = 30 (plafond)
        assertThat(saved.getJoursDroit()).isEqualByComparingTo("30.0");
        assertThat(saved.getJoursPris()).isEqualByComparingTo("5.0");
        assertThat(saved.getJoursRestants()).isEqualByComparingTo("25.0");
    }

    @Test
    void recalculerDroitsNouvelAn_soldeNegatif_rameneAZero() {
        int annee = LocalDate.now().getYear();
        Organisation org = new Organisation();
        org.setId(orgId);

        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);
        // Embauché en cours d'année → peu de droits
        s.setDateEmbauche(LocalDate.of(annee, 11, 1));
        s.setStatut(StatutSalarie.ACTIF);

        when(organisationRepository.findByActifTrue()).thenReturn(List.of(org));
        when(salarieRepository.findByOrganisationIdAndStatut(orgId, StatutSalarie.ACTIF)).thenReturn(List.of(s));
        when(congeRepository.sumJoursValidesSoldeEntreDates(
                        eq(salarieId),
                        eq(StatutConge.VALIDE),
                        eq(TypeConge.ANNUEL),
                        eq(TypeConge.EXCEPTIONNEL),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(new BigDecimal("20.0"));
        when(droitsCongesRepository.findBySalarie_IdAndAnnee(eq(salarieId), anyInt()))
                .thenReturn(Optional.empty());
        when(droitsCongesRepository.save(any(DroitsConges.class))).thenAnswer(inv -> inv.getArgument(0));

        job.recalculerDroitsNouvelAn();

        ArgumentCaptor<DroitsConges> cap = ArgumentCaptor.forClass(DroitsConges.class);
        verify(droitsCongesRepository).save(cap.capture());
        assertThat(cap.getValue().getJoursRestants()).isEqualByComparingTo(BigDecimal.ZERO.setScale(1));
    }
}
