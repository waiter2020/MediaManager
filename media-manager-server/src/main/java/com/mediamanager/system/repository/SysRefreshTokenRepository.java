package com.mediamanager.system.repository;

import com.mediamanager.system.entity.SysRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface SysRefreshTokenRepository extends JpaRepository<SysRefreshToken, Integer> {
    Optional<SysRefreshToken> findByTokenAndRevokedFalse(String token);

    @Modifying
    @Query("UPDATE SysRefreshToken t SET t.revoked = true WHERE t.user.id = :userId")
    void revokeAllByUserId(Integer userId);

    @Modifying
    @Query("DELETE FROM SysRefreshToken t WHERE t.expiresAt < :now OR t.revoked = true")
    void deleteExpiredOrRevoked(Instant now);
}
