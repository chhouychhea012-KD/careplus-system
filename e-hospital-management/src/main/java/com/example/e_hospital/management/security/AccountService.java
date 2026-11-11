package com.example.e_hospital.management.security;

import com.example.e_hospital.management.audit.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final HospitalUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final PasswordResetTokenRepository tokens;
    private final java.time.Clock clock;

    public AccountService(HospitalUserRepository users, PasswordEncoder encoder, AuditService audit,
            PasswordResetTokenRepository tokens, java.time.Clock clock) {
        this.users = users; this.encoder = encoder; this.audit = audit;
        this.tokens = tokens; this.clock = clock;
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ACCOUNT_SELF') and #username == authentication.name")
    public void changePassword(@org.springframework.security.core.parameters.P("username") String username, PasswordChangeForm form) {
        HospitalUser user = users.lockByUsername(username).filter(HospitalUser::isActive)
            .orElseThrow(() -> new IllegalArgumentException("Account unavailable."));
        if (!encoder.matches(form.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("The current password is incorrect.");
        }
        PasswordPolicy.validate(form.getNewPassword());
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("The new passwords do not match.");
        }
        if (encoder.matches(form.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Choose a password different from your current password.");
        }
        user.changePassword(encoder.encode(form.getNewPassword()));
        tokens.invalidate(user.getId(), clock.instant());
        audit.record(username, "PASSWORD_CHANGED", "user:" + user.getId());
    }

    public record Profile(String username, String displayName, String email, String phone, Role role) {}
    @Transactional(readOnly = true)
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ACCOUNT_SELF') and #username == authentication.name")
    public Profile profile(@org.springframework.security.core.parameters.P("username") String username) {
        var user = users.findByUsername(username).filter(HospitalUser::isActive).orElseThrow(() -> new IllegalArgumentException("Account unavailable."));
        return new Profile(user.getUsername(), user.getDisplayName(), user.getEmail(), user.getPhone(), user.getRole());
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ACCOUNT_SELF') and #username == authentication.name")
    public void updateProfile(@org.springframework.security.core.parameters.P("username") String username, ProfileForm form) {
        if (form.getDisplayName() == null || form.getDisplayName().isBlank() || form.getDisplayName().length() > 100
                || form.getPhone() != null && !form.getPhone().matches("[0-9+() .-]{0,30}")) {
            throw new IllegalArgumentException("Enter a name and a valid phone number.");
        }
        var user = users.lockByUsername(username).filter(HospitalUser::isActive).orElseThrow(() -> new IllegalArgumentException("Account unavailable."));
        user.updateProfile(form.getDisplayName().strip(), form.getPhone());
        audit.record(username, "PROFILE_UPDATED", "user:" + user.getId());
    }
}
