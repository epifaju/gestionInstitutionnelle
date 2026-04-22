package com.app.modules.finance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FactureSequenceService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public int nextSequence(UUID organisationId, int annee) {
        List<Integer> rows =
                jdbcTemplate.query(
                        """
                                UPDATE facture_sequences
                                SET derniere_seq = derniere_seq + 1
                                WHERE organisation_id = ? AND annee = ?
                                RETURNING derniere_seq
                                """,
                        (rs, rowNum) -> rs.getInt(1),
                        organisationId,
                        annee);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        jdbcTemplate.update(
                """
                        INSERT INTO facture_sequences (organisation_id, annee, derniere_seq)
                        VALUES (?, ?, 0)
                        ON CONFLICT (organisation_id, annee) DO NOTHING
                        """,
                organisationId,
                annee);
        List<Integer> retry =
                jdbcTemplate.query(
                        """
                                UPDATE facture_sequences
                                SET derniere_seq = derniere_seq + 1
                                WHERE organisation_id = ? AND annee = ?
                                RETURNING derniere_seq
                                """,
                        (rs, rowNum) -> rs.getInt(1),
                        organisationId,
                        annee);
        if (!retry.isEmpty()) {
            return retry.get(0);
        }
        throw new IllegalStateException("Impossible d'obtenir la séquence facture");
    }
}
