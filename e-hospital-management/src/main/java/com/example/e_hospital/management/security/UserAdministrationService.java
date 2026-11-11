package com.example.e_hospital.management.security;

import com.example.e_hospital.management.audit.AuditService;
import com.example.e_hospital.management.web.RecordNotFoundException;
import java.util.Locale;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAuthority('USERS_MANAGE')")
public class UserAdministrationService {
    private final HospitalUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final PasswordResetTokenRepository tokens;
    private final java.time.Clock clock;
    public UserAdministrationService(HospitalUserRepository users, PasswordEncoder encoder, AuditService audit,
            PasswordResetTokenRepository tokens, java.time.Clock clock) {
        this.users = users; this.encoder = encoder; this.audit = audit;
        this.tokens = tokens; this.clock = clock;
    }
    public record UserSummary(Long id, String username, String displayName, String email, Role role, boolean active) {}
    private UserSummary summary(HospitalUser user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getRole(), user.isActive());
    }
    @Transactional(readOnly = true)
    public Page<UserSummary> list(String query, int page) {
        return users.search(query, PageRequest.of(Math.max(0, Math.min(page, 100000)), 15, Sort.by("username"))).map(this::summary);
    }
    @Transactional(readOnly = true)
    public UserSummary get(Long id) { return summary(users.findById(id).orElseThrow(RecordNotFoundException::new)); }
    @Transactional
    public void create(UserForm form) {
        PasswordPolicy.validate(form.getInitialPassword());
        if (users.existsByUsernameIgnoreCase(form.getUsername())) throw new IllegalArgumentException("This username is already in use.");
        String email = email(form.getEmail());
        if (email != null && users.existsByEmailIgnoreCaseAndIdNot(email, -1L)) throw new IllegalArgumentException("This recovery email is already assigned.");
        var user = new HospitalUser(form.getUsername(), form.getDisplayName().strip(), encoder.encode(form.getInitialPassword()), form.getRole());
        user.configureAccess(form.getRole(), form.isActive(), email);
        users.saveAndFlush(user);
        audit.record(actor(), "USER_CREATED", "user:" + user.getId());
    }
    @Transactional
    public String update(Long id, UserForm form) {
        var administrators = users.lockActiveAdministrators();
        var user = users.lockById(id).orElseThrow(RecordNotFoundException::new);
        boolean removesAdministrator = user.getRole() == Role.ADMIN && user.isActive() && (form.getRole() != Role.ADMIN || !form.isActive());
        if (removesAdministrator && (administrators.size() < 2 || user.getUsername().equals(actor()))) {
            throw new IllegalArgumentException("Keep an active administrator and ask another administrator to change your own access.");
        }
        String email = email(form.getEmail());
        if (email != null && users.existsByEmailIgnoreCaseAndIdNot(email, id)) throw new IllegalArgumentException("This recovery email is already assigned.");
        user.updateProfile(form.getDisplayName().strip(), user.getPhone());
        user.configureAccess(form.getRole(), form.isActive(), email);
        audit.record(actor(), "USER_ACCESS_UPDATED", "user:" + id);
        tokens.invalidate(id, clock.instant());
        return user.getUsername();
    }
    private String actor() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private String email(String value) { return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT); }
}
