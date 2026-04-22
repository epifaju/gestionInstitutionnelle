package com.app.modules.auth.dto;

import com.app.modules.auth.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUserCreateRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        String nom,
        String prenom,
        @NotNull Role role) {}
