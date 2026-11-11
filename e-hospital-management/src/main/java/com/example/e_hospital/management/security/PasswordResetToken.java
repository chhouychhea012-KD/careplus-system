package com.example.e_hospital.management.security;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens", indexes = @Index(name = "idx_reset_user", columnList = "user_id"))
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64, unique = true) private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private HospitalUser user;
    @Column(nullable = false) private Instant expiresAt;
    private Instant usedAt;
    protected PasswordResetToken() {}
    public PasswordResetToken(String hash, HospitalUser user, Instant expiresAt) {
        tokenHash = hash; this.user = user; this.expiresAt = expiresAt;
    }
    public boolean usable(Instant now) { return usedAt == null && now.isBefore(expiresAt); }
    public void consume(Instant now) { usedAt = now; }
}
