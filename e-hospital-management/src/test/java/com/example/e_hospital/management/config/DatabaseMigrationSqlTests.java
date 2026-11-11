package com.example.e_hospital.management.config;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigrationSqlTests {
    @ParameterizedTest
    @ValueSource(strings = {"h2", "mysql"})
    void initialMigrationCreatesConstrainedTablesWithoutOverwritingData(String vendor) throws Exception {
        // MySQL SQL is checked in H2 compatibility mode, not against a real MySQL server.
        String url = "jdbc:h2:mem:schema_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            var migration = new ClassPathResource("db/migration/" + vendor + "/V1__identity_and_audit.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            String insert = "insert into hospital_users (version,username,display_name,password_hash,role,active,created_at) "
                + "values (0,'schema-test','Schema Test','noncredential-test-value','ADMIN',true,CURRENT_TIMESTAMP)";
            try (var statement = connection.createStatement()) {
                assertEquals(1, statement.executeUpdate(insert));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + vendor + "/V2__account_recovery.sql"));
                try (var rows = statement.executeQuery("select credential_version, email from hospital_users where username = 'schema-test'")) {
                    assertTrue(rows.next()); assertEquals(0, rows.getLong(1)); assertNull(rows.getString(2));
                }
                assertThrows(SQLException.class, () -> statement.executeUpdate(insert));
                assertThrows(Exception.class, () -> ScriptUtils.executeSqlScript(connection, migration));
                try (var rows = statement.executeQuery("select count(*) from hospital_users")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
                assertDoesNotThrow(() -> statement.executeQuery("select actor,action,target,occurred_at from audit_events").close());
            }
        }
    }
}
