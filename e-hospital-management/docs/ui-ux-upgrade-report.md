# CarePlus UI/UX upgrade report (round 3)

## PDF-driven upgrade, 2026-11-11

This section supersedes the historical delivery/synchronization instructions later in this report. All changes in this upgrade were made directly in the original project. No Java staging or synchronization step is needed.

The existing shared blue/teal design tokens, navigation shell, role dashboards, appointment progress, encounter tabs, queue board, certificates and Mini App layouts were preserved. New work covers several screens and flows:

- Doctor workspace: a visible encounter checklist shows draft notes to sign, pending lab/imaging results and approved results awaiting doctor review. Clicking an item selects the correct encounter tab and preserves other form drafts. The panel distinguishes absence of note/result blockers from clinical approval to discharge.
- Appointment details: recent vitals now come from the linked encounter instead of whichever visit happened to have the latest triage document.
- Director: a dedicated read-only leadership navigation and dashboard; module create/edit/delete/transition controls are suppressed and mutations are refused on the server.
- Imaging: a separate worklist/dashboard/navigation and an imaging-only queue, alongside the existing laboratory role.
- Doctor schedules: dated sessions, breaks, slot length, location and full-day unavailability, with the available slots visible beside the form. Booking conflicts prevent saving an incompatible schedule. Administrators edit; doctors view their own schedules; reception views availability. Scheduling uses the shared service consumed by the patient Mini App.
- Telegram administration: configurable public contact/hours; English/Khmer command preview; webhook enablement/secret readiness/last receipt; bounded command history. Secret values are never displayed. Preview sends no message.
- Shared forms: mutation controls now follow role capabilities; light/dark semantic colors apply to new panels, controls, slot badges and bot replies.

Actual before captures: `screenshots/requirements-before/` (28 views). Actual after captures: `screenshots/requirements-after/` (32 viewport views plus director/imaging dashboards). Both were captured from the original running checkout, not reconstructed mockups. The screenshots may show different synthetic records because workflow tests create data between captures. They do not prove pixel-identical data snapshots.

Viewport combinations: 1440 px English/light, 768 px Khmer/dark, 390 px Khmer/dark and 360 px English/light; seven core pages plus the new schedule page after implementation. `scripts/requirements-ui-test.cjs` also verifies the Khmer bot preview, real form submission for scheduling, imaging station selection and director read-only UI. Results: zero JavaScript errors, failed application requests and horizontal viewport overflow in that sweep. Captures were visually inspected for workspace, mobile Khmer schedule and Telegram configuration.

This is representative browser coverage, not every language × theme × viewport permutation. Physical phones, Telegram WebViews, native camera permissions, formal keyboard/contrast audits and native-speaker Khmer approval remain unverified. Browser print supports certificate PDF output; a server PDF/signature/verification service is not implemented.

Date: 2026-11-11. This round builds on the round 2 work in `upgrade-report.md`, and the problems it addresses are listed in `ui-ux-audit.md`.
Before and after screenshots of the running app (desktop and phone, English and Khmer, light and dark) are in `docs/screenshots/ui-before/` and `docs/screenshots/ui-after/`.

## Status at a glance

| Status | Items |
|---|---|
| **Completed and tested** | One shared, role-based sidebar on every staff page · role dashboards (doctor, receptionist, nurse, laboratory, pharmacist, cashier, administrator) · queue status strip, department filter and confirmations · appointment detail fixes · workspace tab order and scrolling · live certificate preview · certificate history page · doctor task page · server-side table sorting · design tokens and readable type · Khmer for every new screen · consistent dark theme · phone layouts · Mini App icons and doctor Home · cache fix |
| **Partially completed** | Inline field validation and searchable selects in module forms: the existing browser validation (translated in Khmer) and the existing selects are kept as they were · Khmer long-date formatting can't be verified in the test browser (no `km-KH` locale data) |
| **Not implemented** | A separate appointment calendar redesign (the existing calendar view is unchanged) · a doctor schedule editor · real phone and Telegram testing (no device available) |

## Pages reviewed
Login, role dashboards, hospital analytics (`/overview`), all 25 module list pages and their 22 sub-catalogues, record drawers and dialogs, the appointment workspace, patient/encounter workspace (14 tabs), queue, check-in desk, Telegram settings, the new certificate and task pages, the Doctor and Patient Mini Apps, and the Telegram entry page.

## What changed and why

### 1. Navigation: one shell, role-based (audit 1–3, 23)
- `NavigationAdvice` (new, Java) builds the menu for the signed-in role and marks the current page. It is the same on module pages and clinical pages, because both now include `fragments/shell.html`.
- Primary links by role:
  - **Doctor:** Dashboard, My Queue, My Appointments, My Patients, Consultations, Orders and Results, Medical Certificates, Tasks.
  - **Receptionist:** Dashboard, Patient Registration, Appointments, Check-In, Queue Management.
  - **Nurse:** Dashboard, Triage, Vital Signs, Assigned Patients, Queue.
  - **Laboratory:** Dashboard, Laboratory Worklist, Sample Collection, Results, Imaging Worklist.
  - **Pharmacist:** Dashboard, Prescriptions, Dispensing, Medicine Stock.
  - **Cashier:** Dashboard, Invoices, Awaiting Payment, Payments.
  - **Administrator:** Dashboard, Staff, Departments, Hospital Configuration, Reports, Roles and Permissions, Settings. A "Hospital operations" group adds Patients, Appointments, Check-In Desk, Queue, Hospital Analytics and Telegram Mini App.
- Every other module the role may open sits in one collapsible **More modules** group. It never repeats a primary link and never lists a module the server would refuse. The duplicate "Quick access" block is gone.
- Nav text is 13.5 px (it was 10–11 px), group labels 11–12 px, and labels are not uppercased in Khmer.
- The clinical pages gained breadcrumbs (for example Workspace › Appointments › Appointment details), the user menu and sign-out.
- On phones the sidebar becomes a drawer, and the language/theme selects move into the user menu.
- Menus are a convenience only: every permission is still enforced on the server, unchanged.

### 2. Dashboards (audit 4–5)
- `/` now opens a role dashboard (`home.html`, `home.js`) fed by the new `GET /api/care/dashboard` (`HospitalService.roleDashboard`). Every figure comes from the live in-memory store.
  - **Doctor:** current consultation, next patient with *Call next patient*, today's appointments, patients waiting with the longest wait, pending clinical notes, results to review, today's schedule, a task list (notes to write or sign, results to review, certificates to issue) and upcoming appointments.
  - **Receptionist:** registrations today, appointments today, awaiting check-in, checked in, expected arrivals (each opens its appointment), recent check-ins, and the queue at each station.
  - **Nurse:** waiting for triage, visits waiting, in consultation, the triage list, and the queue at each station.
  - **Laboratory, pharmacist, cashier:** their worklists and counts.
  - **Administrator:** appointments and visits today, patients in queue, outstanding balance, department activity chart (Chart.js, theme-aware), staff availability, queue performance (status mix, average wait before call, longest current wait) and billing summary.
- The previous analytics dashboard still works, at **/overview** ("Hospital Analytics").
- The "Viewing as" demo switcher now shows the real role and reloads, so the server-built menu follows the switch.

### 3. Appointment details (audit 6–7)
- "No known allergies" and similar entries now show as a neutral pill; only a real allergy is shown red.
- Clinical roles can open the patient record before check-in.
- Unchanged from round 2: the patient header (name, ID, doctor, department, time, status, queue number), the journey from service data, medical-record tabs, and role/state-aware actions that open the encounter workspace on the right tab.

### 4. Doctor workspace and certificates (audit 8–10, 22)
- **Workspace tabs:** one horizontally scrolling row in clinical order: Overview, Patient Medical History, Triage & Vital Signs, Consultation, Clinical Notes, Diagnoses, Laboratory Orders, Imaging Orders, Prescriptions, Procedures, Medical Certificates, Documents, Follow-Up & Discharge, Encounter Timeline.
- **Deep links and banner:** links now accept `?tab=certificates` and `?tab=notes`. The patient banner links back to the appointment.
- **Certificate composer:**
  - The template selector comes first, and a live A4 preview beside the form shows the patient, age/sex, encounter, doctor, department, purpose, leave dates and the statement as you type.
  - A "DEMO · DRAFT" watermark marks the draft.
  - Steps read Draft → Preview → Review and issue. Issuing is still a clinician action (the existing Review / Finalize step), and Print Preview prints A4.
- **Medical Certificates page** (`/care/certificates`): start a certificate from any of your open encounters; the history can be searched and filtered by status. A doctor sees only their own certificates, an administrator sees all, and other roles are refused (`GET /api/care/documents`).
- **Tasks page** (`/care/tasks`): the doctor's open notes, results and certificate drafts, filterable, each linking to the right encounter tab.

### 5. Queue (audit 11–13)
- A status strip shows Waiting, Called, In service, On hold, Transferred, Completed and Missed, with counts even when zero. Clicking a status lists those patients.
- New department filter (doctor, priority and search were already there).
- Skip and Mark Missed ask for confirmation, and a Missed patient can be resumed from the list.
- Rows show "General Medicine · Dr. Sarah Wilson" instead of "— · …", and the emoji icon is replaced with a Lucide icon.

### 6. Tables (audit 14–15)
- Column headers sort, using `aria-sort` with a visible indicator.
- Sorting happens on the server before paging (`GET /api/records/{module}?sort=&dir=`), so it covers every record and not just the visible page:
  - reference columns sort by their displayed name;
  - numbers sort numerically;
  - dates sort together with the time.
- The appointment stage column no longer prints "— · —".
- Existing row menus (view, edit, actions, delete with confirmation), pagination, filters and empty states are kept.

### 7. Design system (audit 17, 20)
`static/ui/design.css` is loaded last on every page and provides:
- **Semantic tokens:** primary, background, surface, elevated, input, text, muted, border, hover, active, focus ring, success, warning, danger, info.
- **One light and one dark palette** shared by module pages and clinical pages.
- **Components:** page header, cards, tiles, lists, badges, buttons, chips, filters, responsive tables, bars, empty states, loading skeletons and the certificate paper.
- **Typography:** 14 px body text and readable table text. The Khmer font is Noto Sans Khmer (bundled), Khmer is never letter-spaced, and charts use the page font.

### 8. Mobile and Mini Apps (audit 18–19)
- Dashboard tiles are two per row on phones, action buttons share the width, and the queue status strip scrolls sideways.
- Mini App bottom bars and the profile button use Lucide icons.
- The doctor Mini App Home adds a current-patient card, *Call next patient*, *Up next* and *Today's schedule*.
- The patient Mini App, QR check-in, security boundaries and demo-mode restrictions are unchanged.

### 9. Build and caching (audit 21)
- `application.properties` now sends `Cache-Control: no-cache` with Last-Modified for static files, so a rebuilt UI shows up on the next normal page load.
- Tailwind: no new Tailwind classes were introduced (the new styles are plain CSS tokens), so `tailwind.css` does not need rebuilding. `npm run css` still works.

## JavaScript and backend bugs fixed

| Bug | Fix |
|---|---|
| The "Viewing as" select showed the wrong role (`x-model` applied before `x-for` options existed) | Each option carries `:selected`, and the page reloads after a switch |
| `i18n.js` replaced the class of the preference container, so layout classes were lost | Uses `classList.add` |
| Placeholder "Search appointments…" was never translated (the rule existed for text nodes only) | `t()` handles "Search X…" for attributes too |
| The Chart.js instance stored in Alpine state caused "Maximum call stack size exceeded" | Kept outside the reactive state |
| `?tab=certificates` was ignored | Added aliases |
| No-allergy text looked like an allergy alert | Neutral pill |
| Stale UI after upgrade (1-hour static cache) | Revalidation |

## Translation status
- `km.json` has 2,508 keys, 122 of them added this round.
- `npm run test:i18n` reports 0 missing and 0 bad.
- New dynamic phrases ("3 checked in", "Longest wait 20 min", …) keep their numbers.
- A browser sweep in Khmer + dark over 14 pages found no untranslated interface labels; the only English left is patient/staff names, dates, IDs, the brand name and the word "Telegram". Clinical text typed by users is never translated.

## Theme status
- Light, Dark and System work on every page. System follows the operating system live without a reload, and the choice is remembered (`careplus-theme`).
- The automated sweep found no large light surfaces in dark mode on any of the 14 pages (QR codes are intentionally white).

## Mobile status
- At 390 px there is no horizontal overflow on any of the 14 swept pages or the patient Mini App.
- The sidebar is a drawer on phones, and both Mini Apps have bottom navigation with safe-area padding.

## Test results (all on the final build)

| Suite | Result |
|---|---|
| Java (`mvnw -o test`) | **55/55** (47 earlier + 8 new in `RoleNavigationAndDashboardTests`) |
| `npm run test:browser` | **266** assertions, 0 runtime errors (every module, CRUD, dialogs, row menus, filters, mobile) |
| `npm run test:care` | **32** |
| `npm run test:journey` | **70** (receptionist → nurse → doctor → lab → pharmacy → cashier, role navigation, Khmer + dark sweep, phone overflow) |
| `npm run test:qr` | **31** |
| `npm run test:i18n` | 0 missing, 0 bad |

The new Java tests cover:
- the doctor menu (no admin links, current page marked);
- the primary links for every role;
- "More modules" never repeating or exposing modules;
- dashboard content per role, with patients refused;
- doctor tiles equal to the real queue;
- certificate-history access (clinicians only, bad kinds rejected);
- `/overview` still served;
- sorting before paging.

## Files

**New:**
- `controller/NavigationAdvice.java`
- `templates/ui/fragments/shell.html`
- `templates/ui/home.html`, `templates/ui/certificates.html`, `templates/ui/tasks.html`
- `static/ui/design.css`, `static/ui/home.js`, `static/ui/documents.js`
- `RoleNavigationAndDashboardTests.java`
- `docs/ui-ux-audit.md`, this report, `docs/screenshots/ui-before|ui-after/`

**Modified:**
- Java: `CareController.java` (dashboard, certificate and task routes), `WorkspaceController.java` (`/overview`), `HospitalApi.java` (sort), `HospitalService.java`, `InMemoryHospitalService.java` (role dashboards, document history, sorting)
- Templates: `appointment.html`, `queue.html`, `checkin.html`, `telegram.html`, `care.html`, `mini.html`, `workspace.html`, `login.html`, `error.html`, `fragments/clinic.html`, `fragments/layout.html`, `fragments/table.html`
- Scripts and styles: `app.js`, `appointment.js`, `care.js`, `clinic.js`, `i18n.js`, `mini.js`, `queue.js`, `locales/en.json`, `locales/km.json`, `application.properties`
- Tests and sync: `scripts/browser-test.cjs`, `scripts/care-browser-test.cjs`, `scripts/journey-test.cjs` (updated for the new navigation), `java-sync-manifest.txt` (24 Java files)

## Remaining limitations
- Demo login is not real authentication, and all data is synthetic and in memory (Oracle is deliberately not integrated).
- Real Telegram, phone camera and device behaviour have not been tested.
- Khmer long dates rely on the browser's `km-KH` locale data. The headless test browser lacks it, so it falls back to English there.
- A drag-and-drop appointment calendar and a doctor schedule editor were not built this round.

## Run it
In Command Prompt:
```
cd /d "D:\e-hospital management\e-hospital-management"
sync-java.cmd
mvnw.cmd -Dmaven.repo.local=.cache/m2 -o spring-boot:run
```
Then open http://127.0.0.1:8090. `sync-java.cmd` is only needed when Java files arrive through `D:\ehospital-review`. It checks all 24 files against `java-sync-manifest.txt`, copies them, checks them again and runs the tests.
