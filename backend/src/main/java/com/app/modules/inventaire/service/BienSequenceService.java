package com.app.modules.inventaire.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BienSequenceService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public int nextSequence(UUID organisationId, String codeCategorie, int annee) {
        List<Integer> rows =
                jdbcTemplate.query(
                        """
                                UPDATE bien_sequences
                                SET derniere_seq = derniere_seq + 1
                                WHERE organisation_id = ? AND code_categorie = ? AND annee = ?
                                RETURNING derniere_seq
                                """,
                        (rs, rowNum) -> rs.getInt(1),
                        organisationId,
                        codeCategorie,
                        annee);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        jdbcTemplate.update(
                """
                        INSERT INTO bien_sequences (organisation_id, code_categorie, annee, derniere_seq)
                        VALUES (?, ?, ?, 0)
                        ON CONFLICT (organisation_id, code_categorie, annee) DO NOTHING
                        """,
                organisationId,
                codeCategorie,
                annee);
        List<Integer> retry =
                jdbcTemplate.query(
                        """
                                UPDATE bien_sequences
                                SET derniere_seq = derniere_seq + 1
                                WHERE organisation_id = ? AND code_categorie = ? AND annee = ?
                                RETURNING derniere_seq
                                """,
                        (rs, rowNum) -> rs.getInt(1),
                        organisationId,
                        codeCategorie,
                        annee);
        if (!retry.isEmpty()) {
            return retry.get(0);
        }
        throw new IllegalStateException("Impossible d'obtenir la séquence bien");
    }
}
