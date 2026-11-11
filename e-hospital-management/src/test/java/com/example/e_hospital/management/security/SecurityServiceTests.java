package com.example.e_hospital.management.security;

import com.example.e_hospital.management.audit.AuditService;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityServiceTests {
    HospitalUserRepository users;
    PasswordResetTokenRepository tokens;
    AuditService audit;
    ResetDelivery delivery;
    MutableClock clock;
    BCryptPasswordEncoder encoder;
    HospitalUser user;
    String original;
    PasswordRecoveryService recovery;
    @BeforeEach void setup() {
        users = mock(HospitalUserRepository.class); tokens = mock(PasswordResetTokenRepository.class);
        audit = mock(AuditService.class); delivery = mock(ResetDelivery.class); clock = new MutableClock();
        encoder = new BCryptPasswordEncoder(4); original = UUID.randomUUID().toString();
        user = new HospitalUser("clinician", "Clinician", encoder.encode(original), Role.DOCTOR);
        ReflectionTestUtils.setField(user, "id", 1L); user.configureAccess(Role.DOCTOR, true, "clinician@example.invalid");
        when(users.findByUsername("clinician")).thenReturn(Optional.of(user));
        when(users.lockByUsername("clinician")).thenReturn(Optional.of(user));
        when(users.lockById(1L)).thenReturn(Optional.of(user)); when(delivery.available()).thenReturn(true);
        recovery = new PasswordRecoveryService(users, tokens, delivery, encoder, audit, clock);
    }
    @Test void authenticationHashesCredentialsAndAuditsSuccess() {
        var provider = provider(new LoginThrottle(clock));
        var manager = new ProviderManager(provider);
        var auth = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("clinician", original));
        assertTrue(auth.isAuthenticated()); assertNull(auth.getCredentials());
        assertNull(((HospitalPrincipal) auth.getPrincipal()).getPassword());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ACCOUNT_SELF")));
        assertFalse(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("USERS_MANAGE")));
        verify(audit).record("clinician", "LOGIN_SUCCEEDED", "authentication");
    }
    private HospitalAuthenticationProvider provider(LoginThrottle throttle) {
        return new HospitalAuthenticationProvider(name -> users.findByUsername(name).map(HospitalPrincipal::new)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials")), encoder, throttle, audit);
    }
    @Test void repeatedFailuresLockTemporarilyAndRecordSafeAuditEvents() {
        var provider = provider(new LoginThrottle(clock));
        for (int i = 0; i < 5; i++) assertThrows(BadCredentialsException.class,
            () -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("clinician", "incorrect")));
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("clinician", original)));
        verify(audit, times(5)).record("ANONYMOUS", "LOGIN_FAILED", "authentication");
        verify(audit).record("ANONYMOUS", "LOGIN_THROTTLED", "authentication");
        clock.advance(Duration.ofMinutes(15));
        assertTrue(provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("clinician", original)).isAuthenticated());
    }
    @Test void unknownAccountsAndDisabledAccountsUseSameFailureMessage() {
        var provider = provider(new LoginThrottle(clock));
        String unknown = assertThrows(BadCredentialsException.class, () -> provider.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated("missing", original))).getMessage();
        user.configureAccess(Role.DOCTOR, false, user.getEmail());
        String disabled = assertThrows(BadCredentialsException.class, () -> provider.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated("clinician", original))).getMessage();
        assertEquals(unknown, disabled);
    }
    @Test void changePasswordRequiresCurrentPasswordAndInvalidatesRecovery() {
        var service = new AccountService(users, encoder, audit, tokens, clock);
        var form = new PasswordChangeForm(); form.setCurrentPassword("incorrect");
        form.setNewPassword(UUID.randomUUID().toString()); form.setConfirmPassword(form.getNewPassword());
        assertThrows(IllegalArgumentException.class, () -> service.changePassword("clinician", form));
        long oldVersion = user.getCredentialVersion(); form.setCurrentPassword(original);
        service.changePassword("clinician", form);
        assertTrue(encoder.matches(form.getNewPassword(), user.getPasswordHash())); assertFalse(encoder.matches(original, user.getPasswordHash()));
        assertEquals(oldVersion + 1, user.getCredentialVersion()); verify(tokens).invalidate(1L, clock.instant());
        verify(audit).record("clinician", "PASSWORD_CHANGED", "user:1");
    }
    @Test void resetTokensAreRandomAndOnlyHashedValuesArePersisted() {
        recovery.request("clinician");
        var raw = ArgumentCaptor.forClass(String.class); verify(delivery).send(eq(user.getEmail()), raw.capture());
        var saved = ArgumentCaptor.forClass(PasswordResetToken.class); verify(tokens).saveAndFlush(saved.capture());
        assertTrue(raw.getValue().matches("[A-Za-z0-9_-]{43}"));
        assertEquals(TokenSecrets.hash(raw.getValue()), ReflectionTestUtils.getField(saved.getValue(), "tokenHash"));
        assertNotEquals(raw.getValue(), TokenSecrets.generate());
        verify(tokens).invalidate(1L, clock.instant());
    }
    private String prepareToken() {
        String raw = TokenSecrets.generate(); String hash = TokenSecrets.hash(raw);
        when(tokens.findUserId(hash)).thenReturn(Optional.of(1L));
        when(tokens.findByTokenHash(hash)).thenReturn(Optional.of(new PasswordResetToken(hash, user, clock.instant().plus(Duration.ofMinutes(30)))));
        return raw;
    }
    @Test void resetIsSingleUseAndChangesPassword() {
        String raw = prepareToken(), password = UUID.randomUUID().toString();
        assertEquals("clinician", recovery.reset(raw, password, password));
        assertTrue(encoder.matches(password, user.getPasswordHash()));
        assertThrows(IllegalArgumentException.class, () -> recovery.reset(raw, password, password));
        verify(audit).record("clinician", "PASSWORD_RESET_COMPLETED", "user:1");
    }
    @Test void expirationBoundaryAndMalformedTokensAreRejected() {
        String raw = prepareToken(), password = UUID.randomUUID().toString(); clock.advance(Duration.ofMinutes(30));
        assertThrows(IllegalArgumentException.class, () -> recovery.reset(raw, password, password));
        assertThrows(IllegalArgumentException.class, () -> recovery.reset("invalid", password, password));
        assertTrue(encoder.matches(original, user.getPasswordHash()));
    }
    @Test void unknownAndIneligibleAccountsDoNotReceiveResetMail() {
        recovery.request("missing"); user.configureAccess(Role.DOCTOR, false, user.getEmail()); recovery.request("clinician");
        verify(delivery, never()).send(anyString(), anyString()); verify(tokens, never()).saveAndFlush(any());
    }
    @Test void profileEditsCannotChangeRolesOrRecoveryEmail() {
        var form = new ProfileForm(); form.setDisplayName("Updated clinician"); form.setPhone("+66 123 456");
        new AccountService(users, encoder, audit, tokens, clock).updateProfile("clinician", form);
        assertEquals(Role.DOCTOR, user.getRole()); assertEquals("clinician@example.invalid", user.getEmail());
        assertEquals("Updated clinician", user.getDisplayName());
    }
    @Test void byteLimitAndConfirmationAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("short"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("界".repeat(25)));
        String raw = prepareToken();
        assertThrows(IllegalArgumentException.class, () -> recovery.reset(raw, UUID.randomUUID().toString(), "different-password"));
    }
    @Test void recoveryRequestsAreLimitedWithoutAccountLookup() {
        var throttle = new LoginThrottle(clock);
        for (int i = 0; i < 5; i++) assertTrue(throttle.allowRecovery("127.0.0.1"));
        assertFalse(throttle.allowRecovery("127.0.0.1")); clock.advance(Duration.ofMinutes(15));
        assertTrue(throttle.allowRecovery("127.0.0.1"));
    }
    @Test void rotatingUsernamesCannotBypassTheConnectionIpLimit() {
        var throttle = new LoginThrottle(clock);
        for (int i = 0; i < 20; i++) {
            assertFalse(throttle.blocked("account-" + i, "192.0.2.1"));
            throttle.failed("account-" + i, "192.0.2.1");
        }
        assertTrue(throttle.blocked("another-account", "192.0.2.1"));
        assertFalse(throttle.blocked("another-account", "192.0.2.2"));
        clock.advance(Duration.ofMinutes(15));
        assertFalse(throttle.blocked("another-account", "192.0.2.1"));
    }
}
