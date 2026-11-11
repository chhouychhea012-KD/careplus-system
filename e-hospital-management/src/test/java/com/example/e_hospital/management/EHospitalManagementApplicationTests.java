package com.example.e_hospital.management;

import com.example.e_hospital.management.security.HospitalUserRepository;
import com.example.e_hospital.management.security.Role;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EHospitalManagementApplicationTests {
    private static final String ADMIN_PASSWORD = UUID.randomUUID().toString();
    @Autowired HospitalUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired Flyway flyway;
    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void isolatedBootstrap(DynamicPropertyRegistry properties) {
        properties.add("hospital.bootstrap.username", () -> "test-admin");
        properties.add("hospital.bootstrap.password", () -> ADMIN_PASSWORD);
        properties.add("hospital.recovery.enabled", () -> false);
    }

    @Test void contextLoads() {
        assertNotNull(users);
    }

    @Test void migrationsCreateSchemaAndBootstrapStoresOnlyPasswordHash() {
        var admin = users.findByUsername("test-admin").orElseThrow();
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.isActive());
        assertNotEquals(ADMIN_PASSWORD, admin.getPasswordHash());
        assertTrue(encoder.matches(ADMIN_PASSWORD, admin.getPasswordHash()));
        assertEquals(1L, jdbc.queryForObject("select count(*) from audit_events where action = 'ADMIN_INITIALIZED'", Long.class));
        assertEquals("2", flyway.info().current().getVersion().getVersion());
    }

    @Test void rerunningMigrationsDoesNotRecreateSchemaOrData() {
        long before = users.count();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(before, users.count());
    }

    @Test void realTemplatesRenderLoginAndAuthenticatedWorkspaceWithoutSecrets() throws Exception {
        var login = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(login.contains("name=\"_csrf\"")); assertTrue(login.contains("Sign in to your workspace"));
        var session = (MockHttpSession) mvc.perform(post("/login").with(csrf()).param("username", "test-admin")
            .param("password", ADMIN_PASSWORD)).andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
        for (String path : new String[]{"/", "/profile", "/profile/password", "/admin/users", "/admin/users/new", "/admin/audit", "/help"}) {
            var content = mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(content.contains("CareBridge")); assertFalse(content.contains(ADMIN_PASSWORD));
            assertFalse(content.contains(users.findByUsername("test-admin").orElseThrow().getPasswordHash()));
        }
        mvc.perform(get("/page-that-does-not-exist").session(session)).andExpect(status().isNotFound());
    }
}
