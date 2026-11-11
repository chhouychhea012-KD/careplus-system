package com.example.e_hospital.management.web;

import com.example.e_hospital.management.audit.*;
import com.example.e_hospital.management.security.*;
import com.example.e_hospital.management.security.Role;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.view.AbstractView;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Exercises real filters, controllers and method-security services; view rendering is deliberately separate. */
@SpringJUnitConfig(WebSecurityTests.TestConfiguration.class)
@WebAppConfiguration
class WebSecurityTests {
    @org.springframework.boot.test.context.TestConfiguration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfiguration.class, LoginThrottle.class, AccountService.class, AuditService.class,
        AuditReadService.class, UserAdministrationService.class, SessionRevocationService.class,
        PasswordRecoveryService.class, WorkspaceController.class, AccountController.class, RecoveryController.class,
        UserAdministrationController.class, SharedModelAdvice.class, GlobalExceptionHandler.class, FailureController.class})
    static class TestConfiguration {
        @Bean HospitalUserRepository users() { return mock(HospitalUserRepository.class); }
        @Bean PasswordResetTokenRepository tokens() { return mock(PasswordResetTokenRepository.class); }
        @Bean AuditEventRepository events() { return mock(AuditEventRepository.class); }
        @Bean ResetDelivery delivery() { return mock(ResetDelivery.class); }
        @Bean RecoveryDispatcher dispatcher() { return mock(RecoveryDispatcher.class); }
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean @Primary PasswordEncoder testEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean ViewResolver views() {
            return (name, locale) -> {
                if (name.startsWith("redirect:")) return new org.springframework.web.servlet.view.RedirectView(name.substring(9), true);
                return new AbstractView() {
                { setContentType("text/html"); }
                @Override protected void renderMergedOutputModel(Map<String, Object> model,
                        jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) throws Exception {
                    response.getWriter().write("view:" + name);
                }
                };
            };
        }
    }
    @org.springframework.stereotype.Controller
    @org.springframework.boot.test.context.TestComponent
    static class FailureController {
        @GetMapping("/test-not-found") String missing() { throw new RecordNotFoundException(); }
        @GetMapping("/test-unexpected") String unexpected() { throw new RuntimeException("noncredential-private-diagnostic"); }
    }
    @Autowired WebApplicationContext context;
    @Autowired HospitalUserRepository users;
    @Autowired AuditEventRepository events;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired RecoveryDispatcher dispatcher;
    @Autowired UserAdministrationService administration;
    final Map<String, HospitalUser> records = new ConcurrentHashMap<>();
    final List<AuditEvent> audits = new ArrayList<>();
    MockMvc mvc;
    String password;
    @BeforeEach void setup() {
        reset(users, events, tokens, dispatcher); records.clear(); audits.clear(); clock.advance(Duration.ofDays(1));
        password = UUID.randomUUID().toString(); seed("admin", Role.ADMIN, 1L); seed("doctor", Role.DOCTOR, 2L);
        when(users.findByUsername(anyString())).thenAnswer(a -> Optional.ofNullable(records.get(a.getArgument(0))));
        when(users.lockByUsername(anyString())).thenAnswer(a -> Optional.ofNullable(records.get(a.getArgument(0))));
        when(users.lockById(anyLong())).thenAnswer(a -> records.values().stream().filter(u -> u.getId().equals(a.getArgument(0))).findFirst());
        when(users.findById(anyLong())).thenAnswer(a -> records.values().stream().filter(u -> u.getId().equals(a.getArgument(0))).findFirst());
        when(users.existsByUsernameIgnoreCase(anyString())).thenAnswer(a -> records.keySet().stream().anyMatch(name -> name.equalsIgnoreCase(a.getArgument(0))));
        when(users.saveAndFlush(any())).thenAnswer(a -> {
            HospitalUser saved = a.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", records.values().stream().mapToLong(HospitalUser::getId).max().orElse(0) + 1);
            records.put(saved.getUsername(), saved); return saved;
        });
        when(users.lockActiveAdministrators()).thenAnswer(a -> records.values().stream().filter(u -> u.isActive() && u.getRole() == Role.ADMIN).toList());
        when(users.search(anyString(), any())).thenAnswer(a -> new PageImpl<>(new ArrayList<>(records.values()), a.getArgument(1), records.size()));
        when(events.save(any())).thenAnswer(a -> { audits.add(a.getArgument(0)); return a.getArgument(0); });
        when(events.count()).thenAnswer(a -> (long) audits.size());
        when(events.findByActorContainingIgnoreCase(anyString(), any())).thenAnswer(a -> new PageImpl<>(new ArrayList<>(audits), a.getArgument(1), audits.size()));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    private HospitalUser seed(String name, Role role, Long id) {
        var user = new HospitalUser(name, name + " staff", encoder.encode(password), role);
        ReflectionTestUtils.setField(user, "id", id); records.put(name, user); return user;
    }
    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf()).param("username", username).param("password", password))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"))
            .andReturn().getRequest().getSession(false);
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    @Test void publicLoginAndProtectedNavigation() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("login"));
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        var session = login("admin");
        mvc.perform(get("/").session(session)).andExpect(status().isOk()).andExpect(view().name("dashboard")).andExpect(model().attribute("userCount", 2L));
        assertTrue(audits.stream().anyMatch(e -> e.getAction().equals("LOGIN_SUCCEEDED")));
    }
    @Test void invalidCredentialsHaveGenericMessageAndAudit() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", "doctor").param("password", "incorrect"))
            .andExpect(redirectedUrl("/login?error"));
        assertEquals(1, audits.stream().filter(e -> e.getAction().equals("LOGIN_FAILED")).count());
        assertEquals("ANONYMOUS", audits.get(0).getActor());
    }
    @Test void logoutRequiresCsrfAndInvalidatesSession() throws Exception {
        var session = login("doctor");
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        assertTrue(session.isInvalid()); assertTrue(audits.stream().anyMatch(e -> e.getAction().equals("LOGOUT")));
    }
    @Test void roleRestrictionsAreEnforcedOnRoutesAndServices() throws Exception {
        var principal = new HospitalPrincipal(records.get("doctor"));
        mvc.perform(get("/admin/users").with(user(principal))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/audit").with(user(principal))).andExpect(status().isForbidden());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> administration.list("", 0));
    }
    @ParameterizedTest @EnumSource(Role.class)
    void eachDefinedRoleCanAccessItsOwnProfile(Role role) throws Exception {
        var user = seed("role-staff", role, 9L);
        mvc.perform(get("/profile").with(user(new HospitalPrincipal(user)))).andExpect(status().isOk()).andExpect(view().name("profile"));
        mvc.perform(get("/admin/users").with(user(new HospitalPrincipal(user))))
            .andExpect(status().is(role == Role.ADMIN ? 200 : 403));
    }
    @Test void csrfIsRequiredForLoginRecoveryAndProfileChanges() throws Exception {
        mvc.perform(post("/login").param("username", "doctor").param("password", password)).andExpect(status().isForbidden());
        mvc.perform(post("/forgot-password").param("username", "doctor")).andExpect(status().isForbidden());
        mvc.perform(post("/profile").with(user(new HospitalPrincipal(records.get("doctor")))).param("displayName", "Updated"))
            .andExpect(status().isForbidden());
    }
    @Test void profileValidationAndPrivilegeEscalationAreRejected() throws Exception {
        var principal = new HospitalPrincipal(records.get("doctor"));
        mvc.perform(post("/profile").with(user(principal)).with(csrf()).param("displayName", ""))
            .andExpect(status().isOk()).andExpect(view().name("profile")).andExpect(model().attributeHasFieldErrors("form", "displayName"));
        mvc.perform(post("/profile").with(user(principal)).with(csrf()).param("displayName", "Updated").param("role", "ADMIN"))
            .andExpect(view().name("profile")).andExpect(model().hasErrors());
        assertEquals(Role.DOCTOR, records.get("doctor").getRole());
        mvc.perform(post("/profile").with(user(principal)).with(csrf()).param("displayName", "Updated doctor").param("phone", "+66 123"))
            .andExpect(redirectedUrl("/profile"));
        assertEquals("Updated doctor", records.get("doctor").getDisplayName());
    }
    @Test void passwordChangeRevokesOldSessions() throws Exception {
        var session = login("doctor"); String next = UUID.randomUUID().toString();
        mvc.perform(post("/profile/password").session(session).with(csrf()).param("currentPassword", password)
            .param("newPassword", next).param("confirmPassword", next))
            .andExpect(redirectedUrl("/login?changed"));
        assertTrue(session.isInvalid()); assertTrue(encoder.matches(next, records.get("doctor").getPasswordHash()));
    }
    @Test void accessChangesInvalidateExistingSessions() throws Exception {
        var session = login("doctor"); records.get("doctor").configureAccess(Role.DOCTOR, false, null);
        mvc.perform(get("/profile").session(session)).andExpect(redirectedUrl("/login?expired"));
        assertTrue(session.isInvalid());
    }
    @Test void recoveryResponsesDoNotDiscloseAccountExistence() throws Exception {
        for (String name : List.of("doctor", "missing")) {
            mvc.perform(post("/forgot-password").with(csrf()).param("username", name))
                .andExpect(status().isOk()).andExpect(view().name("forgot-password")).andExpect(model().attribute("sent", true));
            verify(dispatcher).submit(name);
        }
    }
    @Test void administratorCanReviewAccountsButCannotRemoveLastAdministrator() throws Exception {
        var principal = new HospitalPrincipal(records.get("admin"));
        mvc.perform(get("/admin/users").with(user(principal))).andExpect(status().isOk()).andExpect(view().name("users"));
        mvc.perform(post("/admin/users/1").with(user(principal)).with(csrf()).param("username", "admin")
            .param("displayName", "Admin").param("role", "DOCTOR").param("active", "true"))
            .andExpect(view().name("user-form")).andExpect(model().hasErrors());
        assertEquals(Role.ADMIN, records.get("admin").getRole());
    }
    @Test void administratorCanCreateEditAndDeactivateAccountsWithHashedPasswords() throws Exception {
        var principal = new HospitalPrincipal(records.get("admin")); String initial = UUID.randomUUID().toString();
        mvc.perform(post("/admin/users/new").with(user(principal)).with(csrf()).param("username", "pharmacy-staff")
            .param("displayName", "Pharmacy Staff").param("role", "PHARMACIST").param("active", "true")
            .param("email", "pharmacy@example.invalid").param("initialPassword", initial)).andExpect(redirectedUrl("/admin/users"));
        var created = records.get("pharmacy-staff"); assertNotNull(created); assertTrue(encoder.matches(initial, created.getPasswordHash()));
        assertEquals(Role.PHARMACIST, created.getRole());
        mvc.perform(post("/admin/users/" + created.getId()).with(user(principal)).with(csrf()).param("username", created.getUsername())
            .param("displayName", "Updated staff").param("role", "ACCOUNTANT").param("active", "false"))
            .andExpect(redirectedUrl("/admin/users"));
        assertEquals(Role.ACCOUNTANT, created.getRole()); assertFalse(created.isActive());
        assertTrue(audits.stream().anyMatch(e -> e.getAction().equals("USER_CREATED")));
        assertTrue(audits.stream().anyMatch(e -> e.getAction().equals("USER_ACCESS_UPDATED")));
    }
    @Test void expiredSessionAndMissingRecordHaveClearOutcomes() throws Exception {
        mvc.perform(get("/profile").with(request -> {
            request.setRequestedSessionId("expired-session"); request.setRequestedSessionIdValid(false); return request;
        })).andExpect(redirectedUrl("/login?expired"));
        mvc.perform(get("/admin/users/999").with(user(new HospitalPrincipal(records.get("admin")))))
            .andExpect(status().isNotFound()).andExpect(view().name("error/404"));
    }
    @Test void errorHandlingReturnsSafeStatusAndViews() throws Exception {
        var principal = new HospitalPrincipal(records.get("admin"));
        mvc.perform(get("/access-denied").with(user(principal))).andExpect(status().isForbidden()).andExpect(view().name("error/403"));
        mvc.perform(get("/test-not-found").with(user(principal))).andExpect(status().isNotFound()).andExpect(view().name("error/404"));
        var result = mvc.perform(get("/test-unexpected").with(user(principal))).andExpect(status().isInternalServerError())
            .andExpect(view().name("error/500")).andExpect(model().attributeExists("errorReference")).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("noncredential-private-diagnostic"));
    }
    @Test void unsupportedHttpMethodsDoNotBecomeServerErrors() throws Exception {
        mvc.perform(put("/profile").with(user(new HospitalPrincipal(records.get("doctor")))).with(csrf()))
            .andExpect(status().isMethodNotAllowed()).andExpect(view().name("error/405"))
            .andExpect(result -> assertTrue(result.getResponse().getHeader("Allow").contains("POST")));
    }
}
