# Account and security operations

## Available routes

| Route | Access and purpose |
| --- | --- |
| `/login` | Public login form; POST requires CSRF |
| `/forgot-password` | Public recovery request; POST requires CSRF; identical responses for all eligible/ineligible accounts |
| `/reset-password` | Public reset form; POST requires CSRF and an unexpired, unused bearer token |
| `/` | Authenticated dashboard with real account/audit counts for administrators and a clinical empty state |
| `/profile` | View own account; edit only display name and contact phone |
| `/profile/password` | Change password after verifying current password |
| `/help` | Account and workspace guidance |
| `/admin/users` | Administrator account list, search, pagination, creation, editing and deactivation |
| `/admin/audit` | Administrator audit history with actor search and pagination |
| `POST /logout` | CSRF-protected logout and session invalidation |

These routes have been exercised through the filter/controller tests. Actual Thymeleaf rendering
is covered by an added full application test but remains pending dependency resolution.

## Roles and profile boundaries

The existing one-role-per-user enum model is retained. Each role maps to immutable permissions in
code; users cannot edit permission mappings or add roles through a profile request.

| Role | Implemented permissions |
| --- | --- |
| ADMIN | ACCOUNT_SELF, USERS_MANAGE, AUDIT_VIEW |
| DOCTOR, NURSE, RECEPTIONIST, PHARMACIST, LAB_TECHNICIAN, ACCOUNTANT | ACCOUNT_SELF |
| Existing RADIOLOGIST, INVENTORY_MANAGER roles | ACCOUNT_SELF |

Clinical permissions are deferred until their corresponding modules exist. Backend route checks
and method security enforce these restrictions. Account methods also verify that the username
matches the authenticated identity. UI visibility is supplementary.

Profile forms bind only permitted fields. Recovery email, role, username, password hashes, active
status, and credential versions are not self-editable. Administrators assign recovery addresses
after verifying ownership out of band. This stage does not implement an email-verification flow.
The last active administrator cannot be removed or demoted, and an administrator cannot deactivate
or demote their own account. Deactivation preserves records and audit history.

Password changes require the current password. Changes/resets invalidate outstanding reset tokens
and increment a persisted credential version. Access and recovery-email changes invalidate tokens
and revoke sessions too. Active status and credential versions are checked on the next request;
the local session registry supports explicit expiry. Ordinary name/phone edits preserve access.

Sessions have a 20-minute inactivity timeout and three-session limit. Cookies retain Priority 1's
HttpOnly, SameSite=Lax and HTTPS defaults. CSRF protection remains enabled for all browser POSTs.
Errors do not display stack traces or exception messages; unexpected errors receive a correlation
reference and a log entry containing exception type and code location without request bodies.

## First administrator

The development/test bootstrap requires environment-supplied credentials and runs only under
`dev` or `test`. No password is hardcoded or printed. For a newly provisioned persistent schema,
an operator may explicitly run the non-web `provision-admin` profile using privately supplied
database and bootstrap credentials; see README. Ordinary persistent startup does not create users.
Provisioning leaves populated user tables unchanged and does not repair an existing installation
that has lost its administrator. Such recovery requires a separately reviewed procedure.

## Optional SMTP recovery

Delivery is disabled until explicitly configured. Requests always return the same generic
response for unknown, disabled, unconfigured and throttled accounts. A bounded asynchronous
executor prevents SMTP delivery latency from disclosing account existence to the HTTP caller.

Tokens use 32 cryptographically random bytes; only SHA-256 hashes are persisted. Links expire
after 30 minutes and are single-use. Issuing a new link invalidates older links for that user.
User-first row locking serializes concurrent requests, consumption and password changes. The
concurrency test confirms that two simultaneous consumption attempts succeed exactly once.

The email link contains its token in a fragment, not a GET query parameter. Local JavaScript moves
it to the CSRF-protected form and removes the fragment from browser history. JavaScript is required
for recovery. The base URL is trusted configuration and never comes from request Host headers.

| Environment variable | Purpose |
| --- | --- |
| `HOSPITAL_RECOVERY_ENABLED` | Set `true` only after delivery is configured |
| `HOSPITAL_PUBLIC_URL` | Trusted application base URL; HTTPS required outside dev/test |
| `HOSPITAL_MAIL_FROM` | Sender address accepted by your mail service |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | SMTP endpoint |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Private SMTP credentials, when required |
| `SMTP_STARTTLS` | Defaults to `true`; encryption is required outside dev/test |
| `SMTP_AUTH` | Defaults to `true`; set `false` only for an appropriate local test server |

For a separately running local mail catcher (such as Mailpit listening on port 1025), set these
variables before the README's development startup command. No mail server is bundled or launched:

```powershell
$env:SPRING_MAIL_HOST = '127.0.0.1'
$env:SPRING_MAIL_PORT = '1025'
$env:SMTP_STARTTLS = 'false'
$env:SMTP_AUTH = 'false'
$env:HOSPITAL_RECOVERY_ENABLED = 'true'
$env:HOSPITAL_PUBLIC_URL = 'http://localhost:8080'
$env:HOSPITAL_MAIL_FROM = 'no-reply@example.invalid'
```

Assign a development recovery email under Staff accounts, then inspect the private mailbox of
that mail catcher. Tokens are not shown in the application or application logs. Remove local
SMTP settings before any persistent deployment. Live SMTP delivery remains unverified in this
environment because the mail dependencies are unavailable.

For production use trusted HTTPS and SMTP TLS endpoints, verified recipients, private credentials,
and appropriate trusted proxy settings. Do not enable SMTP debugging, request-body logging, or
proxy logging that exposes credentials or reset submissions. The adapter rejects mail debugging
when recovery is enabled. Follow [Spring Security's authentication provider documentation](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/dao-authentication-provider.html)
and [Bootstrap's distribution documentation](https://getbootstrap.com/docs/5.3/getting-started/download/)
for the underlying provider and locally served UI assets.

## Migrations and existing installations

Priority 1's V1 migrations and database safety guard remain intact. V2 adds account recovery data;
default persistent startup still uses Hibernate `validate` with Flyway disabled until operator
opt-in. Dev and tests still use disposable in-memory H2 with Flyway and validation.

For a Flyway-managed V1 database, back up and review V2 before enabling `HOSPITAL_MIGRATE=true` for
one startup. Leave it false during ordinary operation. Do not edit applied migrations or use clean,
automatic baselining, destructive Hibernate modes, or a reset to make an upgrade work. An existing
unmanaged schema needs a reviewed adoption plan. MySQL DDL can commit partially; inspect failures
and plan recovery rather than automatically dropping newly created objects or repairing history.
No persistent database was connected to or migrated during this implementation.

## Deployment limitations

- Rate limits are bounded and process-local: five failed attempts per username, twenty per
  connection IP and five recovery requests per IP within a 15-minute window. Username keys are
  hashed, forwarding headers are not trusted, and saturated storage fails closed. Counters reset
  on restart. Multi-instance deployments need a shared rate limiter and configured trusted proxies.
- Recovery delivery uses a bounded in-process queue, not a durable outbox. Failed delivery is
  audited without recipient/token details. Used and expired token retention needs an operational
  policy; tokens are invalidated but not automatically deleted in this stage.
- Account email ownership is verified by the administrator out of band. Clinical record access,
  MFA and hospital modules are outside Priority 2.
- Full Boot 4.1.2, Thymeleaf, SMTP and real MySQL acceptance remain pending. Cached-library tests
  provide backend evidence but do not replace deployment verification.
