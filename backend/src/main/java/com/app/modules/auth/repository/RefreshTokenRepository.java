package com.app.modules.auth.repository;

import com.app.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndUsedFalseAndExpiresAtAfter(String tokenHash, Instant now);

    @Modifying
    @Query("update RefreshToken rt set rt.used = true where rt.utilisateur.id = :userId and rt.used = false")
    int invalidateAllForUser(@Param("userId") UUID userId);
}
