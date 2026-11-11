package com.example.e_hospital.management.config;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseSafetyPolicyTests {
    @Test void shippedDisposableProfilesPassSafetyChecks() throws Exception {
        for (String profile : new String[]{"dev", "test"}) {
            var environment = new MockEnvironment();
            for (String file : new String[]{"application.properties", "application-" + profile + ".properties"}) {
                var properties = org.springframework.core.io.support.PropertiesLoaderUtils.loadProperties(
                    new org.springframework.core.io.ClassPathResource(file));
                properties.forEach((key, value) -> environment.setProperty(key.toString(), value.toString()));
            }
            environment.setActiveProfiles(profile);
            assertDoesNotThrow(() -> DatabaseSafetyPolicy.validate(environment));
            // Simulate a higher priority environment/command-line override of the shipped URL.
            environment.setProperty("spring.datasource.url", "jdbc:mysql://localhost/existing");
            assertThrows(IllegalStateException.class, () -> DatabaseSafetyPolicy.validate(environment));
        }
    }

    private MockEnvironment safe(String profile) {
        var environment = new MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:h2:mem:safety;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
            .withProperty("spring.datasource.driver-class-name", "org.h2.Driver")
            .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
            .withProperty("spring.sql.init.mode", "never")
            .withProperty("spring.flyway.clean-disabled", "true")
            .withProperty("spring.flyway.baseline-on-migrate", "false");
        environment.setActiveProfiles(profile);
        return environment;
    }

    @Test void acceptsDisposableDevAndTestDatabases() {
        assertDoesNotThrow(() -> DatabaseSafetyPolicy.validate(safe("dev")));
        assertDoesNotThrow(() -> DatabaseSafetyPolicy.validate(safe("test")));
    }

    static Stream<String> persistentOrExecutableUrls() {
        return Stream.of("jdbc:mysql://localhost/existing", "jdbc:h2:file:./existing",
            "jdbc:h2:tcp://localhost/~/existing", "jdbc:h2:mem:unsafe;INIT=RUNSCRIPT FROM 'external.sql'",
            "jdbc:h2:mem:unsafe;AUTO_SERVER=TRUE", "jdbc:h2:mem:", "");
    }

    @ParameterizedTest @MethodSource("persistentOrExecutableUrls")
    void rejectsPersistentOrExecutableDevAndTestOverrides(String url) {
        for (String profile : new String[]{"dev", "test"}) {
            var environment = safe(profile).withProperty("spring.datasource.url", url);
            assertThrows(IllegalStateException.class, () -> DatabaseSafetyPolicy.validate(environment));
        }
    }

    @Test void rejectsAlternativeConnectionsAndDestructiveOverrides() {
        for (String[] override : new String[][]{
                {"spring.flyway.url", "jdbc:mysql://localhost/existing"},
                {"spring.datasource.hikari.jdbc-url", "jdbc:mysql://localhost/existing"},
                {"spring.datasource.jndi-name", "java:comp/env/jdbc/Existing"},
                {"spring.datasource.hikari.connection-init-sql", "DROP TABLE hospital_users"},
                {"spring.jpa.hibernate.ddl-auto", "create-drop"},
                {"spring.jpa.properties.hibernate.hbm2ddl.auto", "update"},
                {"spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "drop-and-create"},
                {"spring.sql.init.mode", "always"},
                {"spring.flyway.clean-disabled", "false"},
                {"spring.flyway.baseline-on-migrate", "true"}}) {
            var environment = safe("dev").withProperty(override[0], override[1]);
            assertThrows(IllegalStateException.class, () -> DatabaseSafetyPolicy.validate(environment), override[0]);
        }
    }

    @Test void persistentProfilePermitsOnlyMysqlAndValidation() {
        var environment = safe("production").withProperty("spring.datasource.url", "jdbc:mysql://localhost/e_hospital");
        assertDoesNotThrow(() -> DatabaseSafetyPolicy.validate(environment));
        environment.withProperty("spring.jpa.hibernate.ddl-auto", "update");
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyPolicy.validate(environment));
        assertThrows(IllegalStateException.class, () -> DatabaseSafetyPolicy.validate(safe("production")));
    }

    @Test void errorsNeverEchoConnectionSecrets() {
        var environment = safe("dev").withProperty("spring.datasource.url", "jdbc:mysql://localhost/existing?password=hidden-example");
        var error = assertThrows(IllegalStateException.class, () -> DatabaseSafetyPolicy.validate(environment));
        assertFalse(error.getMessage().contains("hidden-example"));
    }

    @Test void rejectsBadConfigurationBeforeAnyConnectionBeanIsCreated() {
        var connected = new java.util.concurrent.atomic.AtomicBoolean();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(safe("dev").withProperty("spring.datasource.url", "jdbc:mysql://localhost/existing"));
            context.register(DatabaseSafetyConfiguration.class);
            context.registerBean("connectionSentinel", Object.class, () -> {
                connected.set(true);
                return new Object();
            });
            assertThrows(Exception.class, context::refresh);
            assertFalse(connected.get());
        }
    }
}
