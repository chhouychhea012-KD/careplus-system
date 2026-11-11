package com.example.e_hospital.management.security;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String hash);
    @Query("select t.user.id from PasswordResetToken t where t.tokenHash = :hash")
    Optional<Long> findUserId(@Param("hash") String hash);
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.user.id = :id and t.usedAt is null")
    int invalidate(@Param("id") Long userId, @Param("now") Instant now);
}
