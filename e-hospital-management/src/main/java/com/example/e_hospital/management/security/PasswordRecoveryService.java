package com.example.e_hospital.management.security;

import com.example.e_hospital.management.audit.AuditService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService {
    private final HospitalUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final ResetDelivery delivery;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final Clock clock;
    public PasswordRecoveryService(HospitalUserRepository users, PasswordResetTokenRepository tokens,
            ResetDelivery delivery, PasswordEncoder encoder, AuditService audit, Clock clock) {
        this.users = users; this.tokens = tokens; this.delivery = delivery;
        this.encoder = encoder; this.audit = audit; this.clock = clock;
    }
    @Transactional
    public void request(String username) {
        audit.record("ANONYMOUS", "PASSWORD_RESET_REQUESTED", "authentication");
        if (!delivery.available()) return;
        var found = users.lockByUsername(username);
        if (found.isEmpty() || !found.get().isActive() || found.get().getEmail() == null) return;
        var user = found.get();
        tokens.invalidate(user.getId(), clock.instant());
        String rawToken = TokenSecrets.generate();
        tokens.saveAndFlush(new PasswordResetToken(TokenSecrets.hash(rawToken), user, clock.instant().plus(Duration.ofMinutes(30))));
        delivery.send(user.getEmail(), rawToken);
        audit.record("SYSTEM", "PASSWORD_RESET_ISSUED", "user:" + user.getId());
    }
    @Transactional
    public String reset(String rawToken, String newPassword, String confirmation) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        PasswordPolicy.validate(newPassword);
        if (!newPassword.equals(confirmation)) throw new IllegalArgumentException("The new passwords do not match.");
        String hash = TokenSecrets.hash(rawToken);
        // Consistent user-first lock ordering also serializes concurrent reset requests and password changes.
        var userId = tokens.findUserId(hash).orElseThrow(PasswordRecoveryService::invalid);
        var user = users.lockById(userId).filter(HospitalUser::isActive).orElseThrow(PasswordRecoveryService::invalid);
        var token = tokens.findByTokenHash(hash).orElseThrow(PasswordRecoveryService::invalid);
        if (!token.usable(clock.instant())) throw invalid();
        token.consume(clock.instant());
        tokens.flush();
        tokens.invalidate(userId, clock.instant());
        user.changePassword(encoder.encode(newPassword));
        audit.record(user.getUsername(), "PASSWORD_RESET_COMPLETED", "user:" + userId);
        return user.getUsername();
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("This reset link is invalid, expired or already used. Request a new link.");
    }
}
