# CarePlus — E-Hospital Management System

This is the current guide for the UI/mock-data application. The pre-existing `README.md` describes the earlier database stage and is retained as a historical reference.

## Run

Requirements: Java 26 or later, Maven wrapper, and Maven Central access for uncached dependencies. This machine uses JDK 26; compilation targets Java 26.

```powershell
.\mvnw.cmd spring-boot:run
```

Open **http://127.0.0.1:8090**. Enter `admin@example.test` or another demo email, choose a role, and enter the workspace. No password is required. This is a clearly labeled mock login, not production authentication. Role selection establishes synthetic session capabilities checked on the server; it is not production authentication. The application binds to loopback by default.

Prepared offline cache commands for this workspace:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=.cache/m2' -o test
.\mvnw.cmd '-Dmaven.repo.local=.cache/m2' -o spring-boot:run
```

The ignored cache is a local convenience, not part of the project. On a normal connected machine, build the executable JAR with `./mvnw clean verify` or `.\mvnw.cmd clean verify`. In this restricted environment, compilation, tests and `spring-boot:run` are verified; JAR packaging requires uncached Maven plugin dependencies and cannot complete offline.

## Application scope

The 27 major modules include authentication/account management and the dashboard, plus these management areas:

| Area | Routes |
| --- | --- |
| Patient care | `/patients`, `/appointments`, `/doctors`, `/departments`, `/facilities`, `/opd`, `/visits`, `/queues` |
| Clinical | `/emr`, `/consultations`, `/laboratory`, `/imaging`, `/pharmacy` |
| Operations | `/medicines`, `/stock`, `/procurement` |
| Finance | `/billing`, `/payments`, `/insurance` |
| Administration | `/staff`, `/access`, `/notifications`, `/reports`, `/settings`, `/audit` |

Twenty-two supporting screens appear inside their parent modules: specialties; hospital branches/buildings/floors/wards/rooms/room types; lab tests; radiology templates; pharmacy locations; medicine categories/brands/units; stock locations; suppliers/manufacturers; billable services; insurance providers; shifts/leave requests/attendance; notification templates.

Other pages: `/profile`, `/appearance`, `/change-password`, `/forgot-password`, `/reset-password`, and `/queue-display`. Direct record URLs use `/{module}/{stable-record-id}`.

Screens share working search, date/status/domain filters, pagination, details/tabs, pre-filled forms, validation, destructive confirmations, success/error feedback, CSV exports and print layouts. Audit events and posted payments are intentionally immutable. Refund is available for payments. Deletion is blocked when a record is referenced elsewhere.

## Connected mock workflows

- Appointments: three-step booking, linked patient/department/doctor selection, duplicate-slot validation, confirm/reschedule/complete/cancel, calendar.
- OPD: check-in creates a linked visit and queue entry, generates a queue number, and enables a printable registration slip. Repeated check-in is rejected.
- Queues: priority ordering, call next, call individual, mark missed, complete, transfer, cancel. Waiting-room display refreshes every ten seconds and hides patient names.
- Clinical: vital signs, allergies, diagnoses, notes, treatments, certificates, patient history, linked prescriptions/lab/imaging orders, related records and activity timelines.
- Laboratory: order → collect → process → enter result → review → approve. Imaging: request → schedule → examine → report → review → approve.
- Pharmacy/stock: dispensing consumes unexpired batches in expiry order. Insufficient stock is rejected. Counts/adjustments require reasons; low-stock/expiry indicators derive from real mock quantities/dates.
- Procurement: submit → approve → order → receive. Receipt creates a stock batch and cannot be repeated.
- Billing/payments: billable item lines calculate subtotal, discount, tax and total. Issue an invoice, record a mock payment and track its balance. Overpayments and mismatched patients are rejected. Refunds restore the outstanding balance.
- CSV medicine import: download template, upload, preview, confirm. Reference names or IDs resolve to existing catalogs. Up to 200 records; any invalid row rolls back the entire import.
- Documents: in-memory upload/download for PDF, PNG, JPEG and TXT; maximum 5 MB/file and 100 files/workspace. Patient and user profile photos are supported.
- Reports: date filters, status charts, matching tables, CSV export, printable documents. Dashboard charts use current mock records and functional date ranges.
- Preferences/accounts: session profile; browser-saved theme, compact navigation, notification preferences and demo permission matrix. Password/recovery forms validate inputs but never save credentials or send mail.
- Settings: editable in-memory settings records. Real integrations are disconnected and labeled as future work. English and Khmer are available through shared message catalogs. See docs/upgrade-report.md for the connected clinical upgrade.

## Architecture

```text
src/main/java/com/example/e_hospital/management/
  EHospitalManagementApplication.java
  prototype/
    controller/  MVC shell, account preview, JSON API, error handling
    model/       ModuleDefinition, FieldDefinition, RecordPage DTOs
    data/        Module schemas, relationships, supporting catalogs
    service/     HospitalService, InMemoryHospitalService, CsvReader
src/main/resources/
  application.properties
  templates/ui/             Pages and reusable Thymeleaf fragments
  static/ui/                CSS, JS, favicon, vendored browser libraries
src/test/java/.../prototype/ Integration and workflow tests
scripts/                    Asset preparation and browser verification
```

Controllers call `HospitalService`. `InMemoryHospitalService` owns validation, relationships, stable IDs, workflow transitions, stock movement, balances, optimistic version checks, attachments and synthetic audit events. Templates render presentation; Alpine coordinates the UI through the JSON API. An Oracle-backed service/repository adapter can replace the mock implementation in the persistence phase.

The previous checkout's persistence/security code and templates remain unchanged as historical references in their original directories. Maven excludes them from compilation, tests and resource packaging; component scanning includes only `prototype`. Only current application properties, `templates/ui`, and `static/ui` are packaged. **The active application has no database, JDBC/JPA, SQL migrations, real payment processing or message delivery.**

## Frontend assets and tests

Runtime libraries and compiled Tailwind CSS are included under `static/ui`; Node is not needed to launch Spring Boot. Inter is requested from Google Fonts with system font fallbacks. Rebuild assets with:

```powershell
npm ci
node scripts/vendor-assets.cjs
npm run css
```

Tailwind theme colors live in `tailwind.config.js`; shared components, dark mode, responsive/print styling and reduced motion live in `static/ui/app.css`.

Backend verification:

```powershell
.\mvnw.cmd test
```

Start the application, then run actual-browser verification:

```powershell
npm run test:browser
```

The browser suite needs Playwright Chromium (`npx playwright install chromium`) or `PLAYWRIGHT_CHROMIUM_EXECUTABLE` pointing to an existing compatible executable. `HOSPITAL_URL` overrides the default local address. Screenshots and a machine-readable report are written to ignored `test-results/`.

Backend coverage includes every page/API, static assets, CRUD, validation, optimistic conflicts, references, filtering/pagination/CSV, OPD linkage, laboratory sequencing, procurement/dispensing, invoice/payment/refund reconciliation, attachment limits, atomic import, combined age/doctor filters, session/CSRF handling and absence of a DataSource. Browser coverage uses visible controls for all management/supporting record screens, details/tabs, queue calls, calendar, files, print, medicine import, billing/payments/refunds, demo roles, empty states, pagination and mobile navigation.

## Demonstration boundaries

All staff share one in-process mock collection. Restarting the JVM clears changed records and files. Browser preferences remain until browser storage is cleared. UI role selection is not production security. New care and patient endpoints enforce server-side demo roles and patient scope. Certificates and financial printouts are labeled synthetic demonstration documents. Do not enter real patient information. Oracle persistence, production identity, real integrations and clinical governance belong to a future phase.
