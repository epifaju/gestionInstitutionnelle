package com.app.modules.rh.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalarieRequest(
        @NotBlank String nom,
        @NotBlank String prenom,
        @Email String email,
        String telephone,
        @NotBlank String poste,
        @NotBlank String service,
        @NotNull @PastOrPresent LocalDate dateEmbauche,
        @NotBlank String typeContrat,
        String nationalite,
        String adresse,
        @NotNull @Positive BigDecimal montantBrut,
        @NotNull @Positive BigDecimal montantNet,
        @NotBlank String devise
) {
}
