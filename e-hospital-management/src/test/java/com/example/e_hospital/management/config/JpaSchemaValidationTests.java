package com.example.e_hospital.management.config;

import com.example.e_hospital.management.audit.AuditEvent;
import com.example.e_hospital.management.security.HospitalUser;
import com.example.e_hospital.management.security.Role;
import com.example.e_hospital.management.security.PasswordResetToken;
import java.sql.DriverManager;
import java.util.UUID;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class JpaSchemaValidationTests {
    @Test void migratedH2SchemaValidatesAgainstEntitiesAndSupportsPersistence() throws Exception {
        String url = "jdbc:h2:mem:jpa_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migration/h2/V1__identity_and_audit.sql"));
            ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migration/h2/V2__account_recovery.sql"));
            var configuration = new Configuration()
                .addAnnotatedClass(HospitalUser.class).addAnnotatedClass(AuditEvent.class).addAnnotatedClass(PasswordResetToken.class)
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
                .setProperty("hibernate.jdbc.time_zone", "UTC");
            configuration.getProperties().put("hibernate.connection.datasource", new DriverManagerDataSource(url, "sa", ""));
            try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
                var transaction = session.beginTransaction();
                var user = new HospitalUser("jpa-test", "JPA Test", "noncredential-test-value", Role.DOCTOR);
                session.persist(user);
                session.persist(new AuditEvent("jpa-test", "SCHEMA_CHECK", "user"));
                transaction.commit();
                session.clear();
                assertNotNull(user.getId());
                assertEquals(Role.DOCTOR, session.find(HospitalUser.class, user.getId()).getRole());
            }
        }
    }
}
