package com.example.e_hospital.management.security;

import com.example.e_hospital.management.audit.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.junit.jupiter.api.Assertions.*;

/** Real Spring Data repositories, transactions and row locks on a disposable H2 schema. */
@SpringJUnitConfig(PasswordRecoveryPersistenceTests.TestConfiguration.class)
class PasswordRecoveryPersistenceTests {
    @org.springframework.boot.test.context.TestConfiguration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {HospitalUserRepository.class, AuditEventRepository.class})
    @Import({PasswordRecoveryService.class, AuditService.class})
    static class TestConfiguration {
        @Bean DataSource dataSource() throws Exception {
            String url = "jdbc:h2:mem:recovery_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
            var source = new DriverManagerDataSource(url, "sa", "");
            try (var connection = source.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/h2/V1__identity_and_audit.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/h2/V2__account_recovery.sql"));
            }
            return source;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(source);
            factory.setPackagesToScan("com.example.e_hospital.management.security", "com.example.e_hospital.management.audit");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.jdbc.time_zone", "UTC",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
        @Bean MutableClock clock() { return new MutableClock(); }
        @Bean CapturingDelivery delivery() { return new CapturingDelivery(); }
    }
    static class CapturingDelivery implements ResetDelivery {
        final Map<String, String> delivered = new ConcurrentHashMap<>();
        public boolean available() { return true; }
        public void send(String email, String token) { delivered.put(email, token); }
    }
    @Autowired PasswordRecoveryService recovery;
    @Autowired HospitalUserRepository users;
    @Autowired DataSource source;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired CapturingDelivery delivery;
    HospitalUser user;
    @BeforeEach void seed() {
        String unique = UUID.randomUUID().toString();
        user = new HospitalUser("nurse-" + unique, "Recovery test nurse", encoder.encode(UUID.randomUUID().toString()), Role.NURSE);
        user.configureAccess(Role.NURSE, true, unique + "@example.invalid"); user = users.saveAndFlush(user);
    }
    private String issue() { recovery.request(user.getUsername()); return delivery.delivered.remove(user.getEmail()); }
    @Test void resetHashAndForeignKeyArePersistedAndNewRequestsRevokeOlderLinks() {
        String first = issue(), second = issue(), next = UUID.randomUUID().toString();
        var jdbc = new JdbcTemplate(source);
        assertEquals(TokenSecrets.hash(second), jdbc.queryForObject("select token_hash from password_reset_tokens where user_id = ? and used_at is null", String.class, user.getId()));
        assertThrows(IllegalArgumentException.class, () -> recovery.reset(first, next, next));
        recovery.reset(second, next, next);
        assertTrue(encoder.matches(next, users.findById(user.getId()).orElseThrow().getPasswordHash()));
        assertThrows(IllegalArgumentException.class, () -> recovery.reset(second, next, next));
    }
    @Test void databaseBackedExpirationRejectsBoundary() {
        String token = issue(), next = UUID.randomUUID().toString(); clock.advance(Duration.ofMinutes(30));
        assertThrows(IllegalArgumentException.class, () -> recovery.reset(token, next, next));
    }
    @Test void concurrentConsumptionSucceedsExactlyOnce() throws Exception {
        String token = issue(), next = UUID.randomUUID().toString();
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> {
                start.await();
                try { recovery.reset(token, next, next); return true; }
                catch (IllegalArgumentException rejected) { return false; }
            };
            var first = pool.submit(attempt); var second = pool.submit(attempt); start.countDown();
            int successes = (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1L, new JdbcTemplate(source).queryForObject("select count(*) from audit_events where action = 'PASSWORD_RESET_COMPLETED' and target = ?", Long.class, "user:" + user.getId()));
        } finally { pool.shutdownNow(); }
    }
}
