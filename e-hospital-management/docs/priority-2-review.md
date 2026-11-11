# Priority 2 review — 2026-10-29

## Result

The backend checks available with cached libraries pass: **54 tests, zero failures**. The complete
Maven build, complete test suite and application startup are **blocked by dependency resolution**.
They are not accepted as passing. No live browser rendering or SMTP verification was possible.
No persistent database was accessed or changed. Priority 3 was not started.

## Requested checks

| # | Check | Observed result |
| --- | --- | --- |
| 1 | Project compiles | Maven compile exits 1 before compilation: Boot parent 4.1.2 is absent and Central access fails. Supplemental Java 17 compilation passes for main sources except the SMTP adapter. |
| 2 | All automated tests pass | Cannot claim all: the full Maven suite never starts. The selected cached-library suite passes 54/54. Full Boot/template and SMTP tests remain unexecuted. |
| 3 | Application starts | Explicit dev-only startup attempt exits before Spring starts with the same missing-parent error. No HTTP server was started by this review. |
| 4 | Login page renders | Source inspected, including UTF-8, labels, messages and local assets. Real Thymeleaf/browser rendering remains unverified. |
| 5 | Valid/invalid login | Real Spring Security filter/MockMvc tests verify successful authentication, generic failure redirect, credential erasure and audit actions. Visible browser messages are implemented but not rendered in this review. |
| 6 | Logout | Tests verify CSRF protection, redirect and session invalidation. Browser behavior remains pending. |
| 7 | Backend authorization | Route and service method checks pass. Ordinary users cannot grant themselves roles through profile submissions. |
| 8 | Required roles | ADMIN, DOCTOR, NURSE, RECEPTIONIST, PHARMACIST, LAB_TECHNICIAN and ACCOUNTANT are configured and tested. Existing RADIOLOGIST and INVENTORY_MANAGER remain. Every role can view its own profile; only ADMIN can access account administration. |
| 9 | Profile/password pages | Backend profile validation, permitted edits, current-password verification, hashing and session revocation pass. Actual page rendering/manual submissions remain pending. |
| 10 | Reset tokens | Tests verify 256-bit random token generation, hash-only storage, 30-minute expiration, replacement invalidation, single use and concurrent consumption succeeding exactly once. SMTP adapter and delivery remain unverified. |
| 11 | Failed-login throttling | Account limits, window expiry, recovery limits and the IP limit across rotating usernames pass. Storage is process-local. |
| 12 | Secret-safe failed-login audits | Tests verify fixed LOGIN_FAILED/LOGIN_THROTTLED events with anonymous actors and fixed targets. Source inspection finds no password/token payload in these records. Unexpected application logs use exception type/code location rather than request bodies/messages. |
| 13 | CSRF | Enabled; tests reject missing-token POSTs for login, recovery, profile and logout. Thymeleaf's generated form-token integration awaits the full template test. |
| 14 | 403/404/500 pages | Templates exist and status/view/error-detail-exclusion tests pass. Live template rendering remains pending. A separate 405 defect was reproduced and fixed. |
| 15 | Responsive dashboard | Fragment layout, real account/audit statistics, clinical empty state, CSS breakpoints, mobile navigation and JavaScript inspected. JavaScript syntax passes. Actual desktop/tablet/mobile rendering is not verified. |
| 16 | Safe migrations/H2 isolation | V1 remains unchanged. V2 is additive. Both dialect scripts execute on disposable H2; Hibernate validation and unsafe-override rejection pass. Real Flyway startup and real MySQL execution remain pending. Default persistent validate/explicit migration opt-in remain intact. |

Permissions remain intentionally limited to the implemented account workspace: all roles receive
ACCOUNT_SELF; ADMIN additionally receives USERS_MANAGE and AUDIT_VIEW. Clinical permissions are
not claimed or exposed before clinical modules exist.

## Small defect fixed

An authenticated `PUT /profile` with a valid CSRF token was incorrectly classified as an unexpected
server error. The new regression test reproduced **500 instead of 405**. The centralized handler
now returns **405**, an `Allow` header and a friendly page using the existing error fragment.

Changed during this review:

- `src/main/java/com/example/e_hospital/management/web/GlobalExceptionHandler.java`: specific 405 handling.
- `src/main/resources/templates/error/405.html`: reuse the shared error page.
- `src/test/java/com/example/e_hospital/management/web/WebSecurityTests.java`: 405 regression and
  admin-access assertions for all existing roles.
- `src/test/java/com/example/e_hospital/management/security/SecurityServiceTests.java`: verify the
  connection-IP limit cannot be bypassed by changing usernames.
- This report and its README link. Ignored `.build/` contains refreshed verification artifacts.

No authentication flow, dependency version, database migration or database setting was rewritten.

## Executed commands and evidence

Using the existing workspace-local Maven distribution:

```powershell
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.build/maven-home'
.\mvnw.cmd '-Dmaven.repo.local=.build/m2' '-Daether.transport.http.connectTimeout=3000' '-Daether.transport.http.requestTimeout=3000' -B compile
.\mvnw.cmd '-Dmaven.repo.local=.build/m2' -o -B test
```

Both Maven commands exit 1 before compiling/running tests. Online resolution reports
`Permission denied: getsockopt` while retrieving
`org.springframework.boot:spring-boot-starter-parent:pom:4.1.2`. Offline resolution reports the
missing parent. No dependency versions were changed to disguise this blocker.

An explicit startup attempt used `dev`, a named H2 memory URL and the H2 driver, disabled recovery
delivery, a loopback-only address, port 18080, and a generated bootstrap password supplied only
through the child-process environment. `spring-boot:run` exits 1 during parent resolution, before
starting Spring. The generated credential was not printed or retained.

Supplemental commands:

```powershell
$mainSources = @(Get-ChildItem src/main/java -Recurse -Filter *.java |
    Where-Object { $_.Name -ne 'SmtpResetDelivery.java' } |
    Select-Object -ExpandProperty FullName)
& 'C:/Program Files/Java/jdk-26.0.1/bin/javac.exe' --release 17 -parameters `
    -cp '.build/offline/lib/*' -d .build/offline/main @mainSources
& ./.build/RunOfflineChecks.ps1
node --check src/main/resources/static/js/app.js
```

Compilation and JavaScript syntax checks exit 0. The final JUnit run reports **54 started, 54
successful, zero failed, exit 0**. Its log is `.build/offline/priority2-review-tests.log`.
The pre-fix 405 failure is recorded in `.build/offline/priority2-review-regression.log`.

The fallback uses cached Boot 4.1.1, Spring Framework 7.0.9, Spring Security 7.1.1, Spring Data JPA
4.1.1, Hibernate 7.4.5.Final, H2 2.4.240, Mockito 5.23.0 and JUnit Jupiter/Platform 6.0.3. It cannot
certify the configured Boot 4.1.2 graph. Web tests use real filters/controllers/method security
with test repositories and a test view resolver; persistence tests use real H2 repositories and
transactions. Thymeleaf, Flyway and mail libraries are unavailable in the cache. The SMTP adapter
is excluded from supplemental compilation; its test and the full application test are not run.

## Known limitations

- Rate limits are bounded but process-local, reset on restart and are not shared between instances.
  Per-IP limits deliberately ignore untrusted forwarding headers. Capacity exhaustion fails closed.
- Recovery processing uses an in-memory queue, without durable delivery/retry. Delivery must be
  configured explicitly; responses remain generic while disabled. Used/expired tokens have no
  automatic retention cleanup yet.
- Recovery email ownership requires administrator verification outside the application.
- Access changes revoke existing sessions on their next request. They do not cancel a request
  that has already been authorized and is executing.
- Real MySQL DDL can partially commit; the H2 checks do not prove MySQL compatibility. Persistent
  migrations require backup, review and explicit operator opt-in. No persistent migration was run.
- Full compilation, full suite, server startup, actual error-page rendering, responsive browser
  behavior and SMTP delivery remain acceptance gaps. No production-readiness claim is made.

## Exact safe startup

First restore Maven Central access or use your approved Maven mirror/proxy configuration. From a
normal local terminal with a compatible JDK, run:

```powershell
Set-Location 'D:\e-hospital management\e-hospital-management'
.\mvnw.cmd -version
.\mvnw.cmd compile
.\mvnw.cmd test

$credential = Get-Credential -UserName 'dev-admin' -Message 'Choose a temporary development password of at least 12 characters'
try {
    $env:HOSPITAL_ADMIN_USERNAME = 'dev-admin'
    $env:HOSPITAL_ADMIN_PASSWORD = $credential.GetNetworkCredential().Password
    .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev' '-Dspring-boot.run.arguments=--hospital.recovery.enabled=false --server.address=127.0.0.1'
} finally {
    Remove-Item Env:HOSPITAL_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:HOSPITAL_ADMIN_USERNAME -ErrorAction SilentlyContinue
    $credential = $null
}
```

Stop with Ctrl+C. This profile uses disposable memory data, not your persistent MySQL database.
Do not set HOSPITAL_MIGRATE=true or run persistent provisioning for this review. If a conflicting
datasource override exists in your environment, resolve it rather than disabling the safety guard.
If the cache is read-only, add `'-Dmaven.repo.local=.build/m2'` to Maven commands.

In IntelliJ, open the existing Maven project, use Java language level 17 and a compatible SDK,
then run the main application with `--spring.profiles.active=dev --hospital.recovery.enabled=false
--server.address=127.0.0.1` and privately supplied bootstrap environment variables. Do not share
credential-bearing run configurations.

## Manual verification checklist after successful startup

1. Open `http://localhost:8080/login`. Check the logo, labels and styles. Visit `/` while signed out
   and confirm redirection to login. Submit an incorrect password once and check the generic error;
   then sign in correctly using the private credential chosen above.
2. Check the overview's real account/audit counts and explicit clinical empty state. Test at desktop,
   tablet and phone widths (for example 1440, 768 and 390 pixels). Check keyboard navigation, mobile
   menu open/close/Escape, account menu, table scrolling and visible form focus.
3. As ADMIN, create separate DOCTOR, NURSE, RECEPTIONIST, PHARMACIST, LAB_TECHNICIAN and ACCOUNTANT
   accounts with private temporary passwords. In a separate browser session, sign in as each and
   verify its profile/dashboard work while `/admin/users` and `/admin/audit` return 403. Never share
   these passwords in logs, screenshots or reports.
4. Edit a profile name/phone, then try an empty name or invalid phone. Check success/validation
   messages. Recovery email and role must remain uneditable on the profile page.
5. Change a disposable account password: reject an incorrect current password and mismatched new
   passwords; accept a valid change, end active sessions, reject the old password and accept the new.
6. Sign out using the visible logout action. Revisit protected routes and confirm login is required.
   Test inactivity expiry after 20 minutes. Use a disposable account for five wrong-password attempts;
   it should be restricted until its 15-minute window expires.
7. Inspect audit history as ADMIN. Confirm LOGIN_SUCCEEDED, LOGIN_FAILED, LOGIN_THROTTLED and
   account actions appear without password/token payloads.
8. While signed in, visit an unknown URL for the 404 page; use a non-admin account for 403. The 500
   handler is exercised by automated tests. No live diagnostic/backdoor route was added to force errors.
9. A safe manual CSRF rejection check, without real credentials, is:

   ```powershell
   curl.exe -i -X POST http://localhost:8080/login --data 'username=unknown'
   ```

   Expect 403 because the browser-form CSRF token is absent.
10. To test actual recovery, configure a separately running **local** mail catcher and the variables
    in [Account and security operations](accounts-and-security.md). Start dev with
    `--hospital.recovery.enabled=true` instead of the explicit false argument above. Assign a test
    recovery email through ADMIN, then request known and unknown usernames and confirm identical UI
    responses. Use the private inbox link, reset once, reject reuse, request another link and reject
    its predecessor. For manual expiration, leave the app running and wait 30 minutes; do not edit
    persistent records or change the system clock. Dev data disappears on process restart, so recreate
    the disposable accounts after a restart. Never paste reset links into public logs or reports.

The report intentionally distinguishes this future checklist from checks already executed.
