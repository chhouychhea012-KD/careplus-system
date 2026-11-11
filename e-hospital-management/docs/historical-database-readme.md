# E-Hospital Management System

Spring Boot / Spring MVC / Spring Security / Spring Data JPA / Thymeleaf, built with Maven.
This checkout includes account authentication, authorization, account administration and a shared
staff interface. Hospital modules are not implemented in this stage. Full Boot 4.1.2 startup and
template rendering remain pending dependency resolution; see the verification reports below.

## Prerequisites

- JDK 17 or a compatible later JDK. Maven targets Java 17; the inspected machine has JDK 26.0.1.
- The existing Maven wrapper supplies Maven 3.10.0. No global Maven installation is required.
- Access to Maven Central or an organization-managed mirror for dependency downloads.
- MySQL for persistent use. Development and automated tests use in-memory H2 without MySQL.

Spring Boot remains at **4.1.2**. Versions managed by its dependency BOM are not overridden.
See [Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html).

## Build and tests

Run PowerShell from the directory containing `pom.xml`:

```powershell
Set-Location 'D:\e-hospital management\e-hospital-management'
.\mvnw.cmd -version
.\mvnw.cmd compile
.\mvnw.cmd test
```

On Linux/macOS use `./mvnw` in place of `.\mvnw.cmd`.
If the shared dependency cache is read-only, use a writable workspace cache:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=.build/m2' compile
.\mvnw.cmd '-Dmaven.repo.local=.build/m2' test
```

The `@SpringBootTest` explicitly activates `test`, loads a separate H2 URL, and generates a
temporary bootstrap password in memory. It does not require persistent database credentials.
Tests cover startup, migrations, bootstrap password hashing, migration reruns, schema
constraints, and rejection of unsafe database configuration before connection beans are created.

## Database profiles and safety

| Profile | Database | Schema handling | Migration behavior |
| --- | --- | --- | --- |
| Default | MySQL, environment configuration | Hibernate `validate` | Disabled unless explicitly enabled |
| `dev` | Named, disposable in-memory H2 | Hibernate `validate` | Flyway initializes the memory database |
| `test` | Separate in-memory H2; test resources only | Hibernate `validate` | Flyway initializes the test database |

No profile uses Hibernate `update`, `create`, or `create-drop`. SQL auto-initialization is disabled.
Dev data disappears when its JVM exits. H2 file and TCP URLs are rejected under `dev`/`test`.
The startup guard also rejects MySQL URLs in disposable profiles, H2 `INIT` scripts, conflicting
Hikari URLs, separate Flyway URLs, JNDI connections, connection initialization SQL, destructive
schema overrides, Flyway clean enablement, and automatic Flyway baselining. It checks resolved
properties, including higher-priority environment and command-line overrides, before database
beans are instantiated. This protects the supplied auto-configuration; review the guard whenever
introducing a custom DataSource or another database-initialization mechanism.

## Environment variables

| Variable | Purpose |
| --- | --- |
| `DB_URL` | MySQL JDBC URL; defaults to `jdbc:mysql://localhost:3306/e_hospital?connectionTimeZone=UTC` |
| `DB_USERNAME` | Required persistent database account name |
| `DB_PASSWORD` | Required persistent database password |
| `HOSPITAL_ADMIN_USERNAME` | First administrator username under `dev`, `test`, or explicit `provision-admin` only |
| `HOSPITAL_ADMIN_PASSWORD` | First administrator password: at least 12 characters, at most 72 UTF-8 bytes; never a default credential |
| `HOSPITAL_MIGRATE` | Defaults to `false`; explicit `true` enables reviewed migrations on persistent startup |
| `SESSION_COOKIE_SECURE` | Defaults to `true` for persistent HTTPS deployment; dev explicitly uses `false` |

Provide secrets through local process environment or your deployment secret manager. Do not
put credentials in JDBC URLs, application files, command-line arguments, committed IDE run
configurations, screenshots, or logs. A `.env` file is **not** automatically loaded by Spring Boot.
Never store or share real secrets in documentation. Bootstrap settings are ignored once users exist;
remove the bootstrap password from the runtime environment after persistent initialization.

## Safe development startup

The following prompts for a temporary development password without putting it in shell history:

```powershell
$env:HOSPITAL_ADMIN_USERNAME = 'dev-admin'
$devCredential = Get-Credential -UserName 'dev-admin' -Message 'Choose a temporary development password (at least 12 characters)'
try {
    $env:HOSPITAL_ADMIN_PASSWORD = $devCredential.GetNetworkCredential().Password
    .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
} finally {
    Remove-Item Env:HOSPITAL_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:HOSPITAL_ADMIN_USERNAME -ErrorAction SilentlyContinue
    $devCredential = $null
}
```

Stop with Ctrl+C. Do not provide a `spring.datasource.url` override under `dev`. If your shell
already has a conflicting Spring datasource override, the safety guard rejects startup rather
than connecting to it. After successful startup, sign in at `http://localhost:8080/login`.
This is an instruction for a dependency-resolved environment; full server startup remains unverified here.

## Persistent MySQL setup and migration strategy

1. Ask your database administrator to provision a **new, dedicated empty schema** and appropriate
   accounts. Never reuse or reset an existing database to make startup work.
2. Supply `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` securely. Use TLS for remote MySQL connections.
3. Review both migrations in `src/main/resources/db/migration/mysql/`. V1 is unchanged. V2 adds
   nullable email/phone fields, a default-zero credential version, an email index and reset-token
   storage with a user foreign key. No tables or rows are dropped or replaced.
4. For this explicitly empty schema only, supply first-admin environment settings and opt in to
   migration for one startup. With the required secrets already supplied in your process:

   ```powershell
   $env:HOSPITAL_MIGRATE = 'true'
   try {
       .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=provision-admin'
   } finally {
       Remove-Item Env:HOSPITAL_MIGRATE -ErrorAction SilentlyContinue
   }
   ```

5. Provisioning is explicitly non-web and only initializes an empty user table. Default persistent
   startup never automatically creates an administrator. Remove bootstrap secrets after provisioning.
   For subsequent starts, leave `HOSPITAL_MIGRATE` unset/false and run:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

Persistent sessions require HTTPS by default. Terminate TLS in your deployment before using
authentication. Development HTTP uses the separate `dev` profile.

Flyway uses vendor-specific migrations, followed by Hibernate schema validation, as described in
the [Spring Boot database initialization documentation](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
Applied migrations must remain immutable; create a new version for later reviewed changes.
Flyway clean and automatic baseline-on-migrate remain disabled.

**Existing populated schemas:** do not enable migration or apply V1 blindly. Back up the schema,
compare its structure with the entities and V1, and prepare a reviewed adoption/baseline plan.
An unmanaged non-empty schema is expected to fail migration rather than be silently adopted.
MySQL DDL is not generally transactional; a failed migration can leave newly created tables.
Inspect failures and plan recovery; do not automatically clean, repair, drop or rerun modified SQL.
Use a migration account only during reviewed migration windows and a restricted runtime account
for ordinary startup.

## IntelliJ IDEA

1. Open the existing `pom.xml` as a Maven project and reload it.
2. Set Project SDK to an installed compatible JDK (17 or later) and language level to 17.
3. Set Maven importer/runner JDK to the same SDK and Maven home to **Use Maven wrapper**.
   JDK 26 on this machine is within Boot 4.1.2's documented compatibility range; it is not a reason
   to change the project target or upgrade the application dependencies.
4. Create a local Application configuration for
   `com.example.e_hospital.management.EHospitalManagementApplication`.
5. Set program arguments to `--spring.profiles.active=dev` and supply development bootstrap
   credentials in a private, unshared run configuration. Do not add persistent database overrides.
6. Run tests through Maven or the IDE; the context test selects `test` itself.

Do not commit workspace SDK paths or credential-bearing run configurations. The inspected `.idea`
metadata does not establish a project SDK, so verify it in the IDE.

## Verification status

See [Priority 1 verification](docs/priority-1-verification.md) and
[Priority 2 verification](docs/priority-2-verification.md) for executed commands and limits.
The subsequent [Priority 2 review](docs/priority-2-review.md) records the latest test results,
the small 405 correction and the manual verification checklist.
Full Maven compilation and application startup remain unverified until dependencies can resolve.

## Account access, recovery and deployment

See [Account and security operations](docs/accounts-and-security.md) for routes, role permissions,
SMTP environment variables, local mail-catcher setup, migration of an existing V1 installation,
and deployment limitations. SMTP recovery is disabled until explicitly configured; request
responses remain generic for all accounts. Never put real credentials or recovery tokens in logs,
documentation or shared IDE run configurations.
