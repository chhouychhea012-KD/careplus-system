# Priority 2 verification — 2026-10-29

## Outcome and limits

Priority 2 source changes are implemented. **52 database, service and web-security checks passed**
using workspace-local cached libraries. Full Maven/Boot 4.1.2 acceptance is **blocked**, not passed.
The shell cannot download the missing parent POM or the uncached Thymeleaf, Flyway and mail
libraries. No persistent database was connected to, migrated, reset or dropped. No hospital module
was implemented.

The shared UI and SMTP adapter are present in source, but actual Thymeleaf rendering, browser
appearance, mail-adapter compilation/live delivery, real MySQL execution, explicit provisioning
startup and the full Boot application context remain unverified in this environment. Tests for
template rendering and mail-message composition were added for the normal Maven suite.

## Changed files

Paths below are relative to the existing Maven project. V1 SQL and Priority 1's safety guard were
not changed. Historical Priority 1 verification documentation was preserved.

### Build/configuration

- `pom.xml`: add Boot-managed mail starter and locally served `org.webjars:bootstrap:5.3.8`.
- `src/main/resources/application.properties`: optional recovery settings and bounded SMTP timeouts,
  TLS/authentication defaults and disabled mail debugging; persistent database settings preserved.
- `src/main/resources/application-provision-admin.properties`: explicit non-web provisioning profile.
- `src/main/resources/db/migration/h2/V2__account_recovery.sql` and
  `src/main/resources/db/migration/mysql/V2__account_recovery.sql`: additive account fields and
  hashed reset-token storage. V1 scripts remain immutable.

### Java source

Under `src/main/java/com/example/e_hospital/management/security/`:

- Modified `HospitalUser`, `HospitalUserRepository`, `Role`, `AdminBootstrap`, `AccountService`,
  and `SecurityConfiguration`.
- Added `Permission`, `HospitalPrincipal`, `HospitalAuthenticationProvider`, `LoginThrottle`,
  `SessionRevocationService`, `SessionValidityFilter`, `ProfileForm`, `UserForm`,
  `UserAdministrationService`, `TokenSecrets`, `PasswordResetToken`,
  `PasswordResetTokenRepository`, `PasswordRecoveryService`, `ResetDelivery`,
  `SmtpResetDelivery`, and `RecoveryDispatcher`.

Added `audit/AuditReadService.java` and these classes under the new `web/` package:

- `WorkspaceController`, `AccountController`, `RecoveryController`,
  `UserAdministrationController`, `SharedModelAdvice`, `GlobalExceptionHandler`,
  and `RecordNotFoundException`.

### UI resources

Under `src/main/resources/templates/`:

- `login.html`, `dashboard.html`, `profile.html`, `password.html`, `forgot-password.html`,
  `reset-password.html`, `users.html`, `user-form.html`, `audit.html`, `help.html`.
- Reusable `fragments/head.html`, `layout.html`, `public.html`, `alerts.html`, and `error.html`.
- Friendly `error/400.html`, `403.html`, `404.html`, `409.html`, and `500.html`.

Added `src/main/resources/static/css/app.css`, `css/logo.svg`, and `js/app.js`. The stylesheet
provides the responsive white/light-blue/navy layout. Forms have labels, validation summaries,
submission feedback, unsaved-change warnings and deactivation confirmation. Mobile navigation
uses inert state when hidden; public forms include secure recovery handling. No patient records
or clinical statistics were invented.

### Tests and documentation

- Modified `EHospitalManagementApplicationTests`: expect migration version 2, disable external
  recovery delivery, and add real-template/CSRF/secret-exclusion assertions for the full Boot suite.
- Modified `config/DatabaseMigrationSqlTests` and `config/JpaSchemaValidationTests` to validate V2
  while preserving the V1 data-preservation checks.
- Added `security/MutableClock`, `SecurityServiceTests`, `PasswordRecoveryPersistenceTests`,
  `SmtpResetDeliveryTests`, and `web/WebSecurityTests`.
- Updated `README.md`; added `docs/accounts-and-security.md` and this report.
- Ignored `.build/` contains cached jars, compiled classes, the supplemental JUnit launcher,
  `.build/RunOfflineChecks.ps1`, and `.build/offline/priority2-tests.log`. These are local
  verification artifacts, not production source or replacement Maven configuration.

## Implemented behavior and executed checks

The executed checks use real security filters/controllers/services; web tests deliberately
substitute a view resolver and repository test doubles. Separate persistence tests exercise real
Spring Data repositories, transactions and locking on H2.

- Successful login, BCrypt verification, credential erasure and success auditing.
- Generic invalid/disabled/unknown login failures, failed-login auditing, temporary limits and expiry.
- CSRF rejection for login, recovery, profile and logout; successful logout invalidates the session.
- Backend route and method authorization, including all nine retained roles' own-profile access.
- Validation and rejection of profile role escalation; permitted name/phone edits.
- Current-password verification, password hashing and old-session revocation.
- Administrator account creation, editing and deactivation; protection of the last active admin.
- Active/credential-version session checks and expired-session outcomes.
- Generic recovery responses, request throttling, hashed token storage, expiration and reuse rejection.
- Real database-backed recovery requests and consumption, invalidation of older links, and two
  simultaneous consumption attempts succeeding exactly once.
- Safe 403/404/500 status/view handling without echoing exception details.
- Preserved database safety checks, V1-to-V2 SQL compatibility in H2, and Hibernate schema validation.

## Actual commands and results

The existing wrapper was run with its workspace-local cached Maven distribution:

```powershell
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.build/maven-home'
.\mvnw.cmd '-Dmaven.repo.local=.build/m2' '-Daether.transport.http.connectTimeout=3000' '-Daether.transport.http.requestTimeout=3000' -B compile
.\mvnw.cmd '-Dmaven.repo.local=.build/m2' -o -B test
```

Both exited **1 before compilation/test execution**. Online compilation failed downloading
`org.springframework.boot:spring-boot-starter-parent:pom:4.1.2` with `Permission denied: getsockopt`.
Offline tests failed because that parent is absent. The configured versions were not downgraded.

Supplemental compilation used:

```powershell
$mainSources = @(Get-ChildItem src/main/java -Recurse -Filter *.java |
    Where-Object { $_.Name -ne 'SmtpResetDelivery.java' } |
    Select-Object -ExpandProperty FullName)
& 'C:/Program Files/Java/jdk-26.0.1/bin/javac.exe' --release 17 -parameters `
    -cp '.build/offline/lib/*' -d .build/offline/main @mainSources
& ./.build/RunOfflineChecks.ps1
node --check src/main/resources/static/js/app.js
```

- Cached-library compilation of all main sources **except the SMTP adapter**: exit 0.
- Supplemental JUnit run: **52 tests found, 52 passed, zero failed, exit 0**.
- JavaScript syntax check: exit 0.

The supplemental run uses cached Boot 4.1.1, Spring Framework 7.0.9, Spring Security 7.1.1,
Spring Data JPA 4.1.1, Hibernate 7.4.5.Final, H2 2.4.240, Mockito 5.23.0, and JUnit Jupiter/Platform
6.0.3. The fallback classpath explicitly excludes an incompatible older ANTLR runtime found in
the broad cache. This classpath is not a certification of the unresolved Boot 4.1.2 dependency graph.
MySQL SQL ran only in H2 compatibility mode. Test configuration classes are marked as test-only
so they cannot contaminate the full application component scan.

The first runs exposed an overly broad session-expiry redirect for CSRF failures, binder target
assumptions, and a test view resolver that did not implement redirects. These were corrected and
the final suite passed. No application security check was disabled to make the tests pass.

## Startup and remaining acceptance

Use the README's secure development credential prompt, then run:

```powershell
.\mvnw.cmd compile
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

After dependencies resolve and the server starts, visit `http://localhost:8080/login`. IntelliJ
should use the existing Maven project, Java language level 17, a compatible SDK, the `dev` program
argument and private environment-supplied bootstrap credentials. SMTP configuration is optional
and documented in `accounts-and-security.md`. Persistent deployment still requires reviewed
migrations, environment-supplied credentials and HTTPS; no persistent migration was executed here.

Before accepting Priority 2 as fully complete, run the normal Maven suite and full Boot startup,
verify real Thymeleaf rendering and responsive browser behavior, exercise SMTP delivery against a
private test mailbox, and verify both migrations on a newly provisioned MySQL test schema. Process-local
rate limiting, in-process mail delivery, manual recovery-email verification and token retention
limits are documented in `accounts-and-security.md`. Work stops at Priority 2 for review.
