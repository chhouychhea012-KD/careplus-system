package com.example.e_hospital.management.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Fail closed before database initialization; never include connection values in errors. */
final class DatabaseSafetyPolicy {
    private static final Set<String> MEMORY_OPTIONS = Set.of(
        "MODE=MYSQL", "DB_CLOSE_DELAY=-1", "DB_CLOSE_DELAY=0",
        "DB_CLOSE_ON_EXIT=FALSE", "DATABASE_TO_LOWER=TRUE", "DATABASE_TO_UPPER=FALSE");

    private DatabaseSafetyPolicy() {}

    static void validate(Environment environment) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            String[] parts = url.split(";", -1);
            require(parts[0].matches("jdbc:h2:mem:[A-Za-z0-9_-]+"),
                "The dev and test profiles require a named, disposable in-memory H2 database.");
            require(Arrays.stream(parts).skip(1)
                    .allMatch(option -> MEMORY_OPTIONS.contains(option.toUpperCase(Locale.ROOT))),
                "An unsupported H2 connection option was rejected for a disposable profile.");
            require("org.h2.Driver".equals(environment.getProperty("spring.datasource.driver-class-name")),
                "Disposable profiles must use the H2 driver.");
        } else {
            require(url.startsWith("jdbc:mysql:"), "The persistent profile requires a MySQL JDBC URL.");
        }
        require("validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto")),
            "Hibernate schema management must remain in validate mode.");
        require("never".equals(environment.getProperty("spring.sql.init.mode")),
            "SQL script auto-initialization must remain disabled; use reviewed Flyway migrations.");
        require(environment.getProperty("spring.flyway.clean-disabled", Boolean.class, false),
            "Flyway clean must remain disabled.");
        require(!environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class, true),
            "Automatic Flyway baselining is prohibited; existing schemas require manual review.");

        // Separate connections could bypass the profile URL check.
        for (String key : Set.of("spring.flyway.url", "spring.datasource.jndi-name",
                "spring.datasource.hikari.data-source-jndi", "spring.datasource.hikari.data-source-class-name",
                "spring.datasource.hikari.connection-init-sql")) {
            require(environment.getProperty(key, "").isBlank(),
                "Separate database connections and connection initialization SQL are unsupported.");
        }
        String hikariUrl = environment.getProperty("spring.datasource.hikari.jdbc-url", "");
        require(hikariUrl.isBlank() || hikariUrl.equals(url),
            "The Hikari connection URL must match spring.datasource.url.");
        require("validate".equals(environment.getProperty("spring.jpa.properties.hibernate.hbm2ddl.auto", "validate")),
            "Native Hibernate schema overrides must remain in validate mode.");
        require("none".equals(environment.getProperty(
                "spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "none")),
            "JPA database schema generation must remain disabled.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
