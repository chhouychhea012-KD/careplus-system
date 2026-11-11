# Priority 1 verification — 2026-10-29

## Scope and preserved configuration

No persistent database was connected to, migrated, reset, dropped, or overwritten during this
stage. No hospital module or Priority 2 UI/authentication workflow was implemented. Spring Boot
4.1.2, Java target 17, Maven wrapper 3.10.0, and the existing package/service/repository architecture
were retained. All database execution in the checks below used disposable, named H2 memory URLs.

## Changes

| Files | Change |
| --- | --- |
| `pom.xml` | Add Boot-managed Flyway starter and MySQL Flyway runtime support |
| `src/main/resources/application.properties` | Persistent Flyway opt-in; clean and automatic baseline disabled; vendor migration locations |
| `src/main/resources/application-dev.properties` | Replace `create-drop` with Flyway plus Hibernate `validate`; retain in-memory H2 |
| `src/main/java/com/example/e_hospital/management/config/DatabaseSafetyConfiguration.java` | Register a pre-instantiation database safety check |
| `src/main/java/com/example/e_hospital/management/config/DatabaseSafetyPolicy.java` | Reject unsafe URL, alternate connection, and schema-generation overrides |
| `src/main/java/com/example/e_hospital/management/security/HospitalUser.java` | Explicit VARCHAR enum mapping to keep the existing role model consistent with migration columns |
| `src/main/resources/db/migration/h2/V1__identity_and_audit.sql` | Initial H2 schema, without destructive SQL or seed credentials |
| `src/main/resources/db/migration/mysql/V1__identity_and_audit.sql` | Initial MySQL schema, without destructive SQL or seed credentials |
| `src/test/resources/application-test.properties` | Isolated test database configuration |
| `src/test/java/com/example/e_hospital/management/EHospitalManagementApplicationTests.java` | Startup, migration rerun and bootstrap persistence checks; generated ephemeral password |
| `src/test/java/com/example/e_hospital/management/config/DatabaseSafetyPolicyTests.java` | Profile configuration, override rejection and fail-before-connection tests |
| `src/test/java/com/example/e_hospital/management/config/DatabaseMigrationSqlTests.java` | Execute both SQL variants on H2 and verify uniqueness and preservation of existing rows |
| `src/test/java/com/example/e_hospital/management/config/JpaSchemaValidationTests.java` | Validate migrated H2 schema against both entities and exercise persistence |
| `README.md` | Prerequisites, private environment configuration, migration adoption, IntelliJ and startup instructions |
| This document | Actual results and remaining verification limits |

Ignored `.build/` contains local dependency copies, compiler outputs and an offline JUnit launcher.
These are verification artifacts, not application source or production dependencies. The existing
Windows wrapper path repair was inspected and exercised; it was not changed in this stage.

## Maven results

`java -version` reported Oracle JDK 26.0.1. The wrapper's `-version` command successfully reported
Maven 3.10.0 and that JDK. Java 26 is within Boot 4.1.2's published compatibility range, so neither
the Boot version nor Java compilation target was changed. IntelliJ's actual SDK choice must be
checked in its settings; the existing metadata does not establish it.

The installed Maven was invoked with these arguments for the online compile attempt:

```text
-Dmaven.repo.local=.build/m2
-Daether.transport.http.connectTimeout=5000
-Daether.transport.http.requestTimeout=5000
-B compile
```

**Result: exit 1, before compilation.** Downloading
`org.springframework.boot:spring-boot-starter-parent:pom:4.1.2` from Maven Central failed with
`Permission denied: getsockopt`. An independent HTTPS request also could not reach Maven Central.
This is a dependency/network blocker, not evidence that the configured version is invalid.

The wrapper was then exercised with a workspace-local copy of the existing Maven distribution:

```powershell
.\mvnw.cmd -version
.\mvnw.cmd '-Dmaven.repo.local=.build/m2' -o -B compile
.\mvnw.cmd '-Dmaven.repo.local=.build/m2' -o -B test
```

- Wrapper startup: **passed**.
- Compile: **exit 1**, missing parent POM in offline cache.
- Test: **exit 1**, same resolution failure before tests could execute.

The read-only shared cache also prevented Maven 3.10 lock-file creation, which is why all final
Maven attempts used a writable `.build/m2`. No changes were made to the shared cache.

## Additional executed checks

Because the complete 4.1.2 dependency graph was unavailable, cached libraries were copied into
the ignored workspace `.build/offline/lib` directory for supplemental verification.

1. All main Java sources compiled with `javac --release 17`: **exit 0**. This used cached Boot
   4.1.1 / Spring Framework 7.0.9 libraries; it does **not** certify the configured Boot 4.1.2 build.
2. The three database-focused test classes were compiled with `javac --release 17` and executed
   through the JUnit Platform launcher: **16 tests passed, zero failed, exit 0**.
3. The passing checks include Spring bean-factory rejection before a connection sentinel can be
   instantiated, unsafe dev/test URL rejection, migration SQL execution, unique username behavior,
   preservation of rows after a duplicate schema-creation attempt, Hibernate schema validation,
   and actual entity persistence/readback.

The offline tests used JUnit Jupiter 5.10.2 / Platform 1.10.2, cached Spring Framework 7.0.9,
Hibernate 7.4.5.Final, and H2 2.4.240. Both SQL dialect scripts ran only on H2; the MySQL script
was checked in H2's MySQL compatibility mode. The offline launcher reported duplicate SLF4J
providers from the broad local cache; that warning is not evidence of a conflict in the unresolved
Maven dependency graph. Workspace-local copies avoided a JDK ZIP filesystem access error when
compiling directly against the restricted shared cache.

The offline run can be inspected locally at `.build/RunOfflineChecks.ps1` and
`.build/OfflineDatabaseChecks.java`. It is a fallback check, not the normal project build path.

## Remaining checks

- Resolve the configured Boot 4.1.2 and Flyway/Thymeleaf dependencies through an accessible Maven
  Central connection or an approved organization mirror, then run `./mvnw compile` and `./mvnw test`.
- Execute the improved full `@SpringBootTest`; Flyway startup and migration rerun assertions
  could not run in the blocked Maven build.
- Verify MySQL-specific migration and Hibernate validation on a separately provisioned empty
  test schema before migrating any persistent deployment.
- Start the real application under `dev` once dependencies resolve. Server startup remains
  unverified; missing UI routes remain explicitly reserved for Priority 2.

Priority 1 source/configuration changes are prepared, but full build and startup acceptance are
**blocked**, not passed. No work on Priority 2 should begin before this stage is reviewed.
