# Full system audit — CarePlus E-Hospital (11 November 2026, round 2)

Authoritative source: `D:\e-hospital management\e-hospital-management`. The project is **not a Git repository** (no `.git` folder), so there was no Git status to preserve. Before changing anything, every file in the original project was listed and compared (size and modification time) with the last verified delivery. Nothing had been edited by another tool in the meantime.

Other folders (`D:\ehospital`, `D:\ehospital-backup-…`) were not used. `D:\ehospital-review` is a **transfer folder only**: the file bridge cannot write Java files nested 8–9 folders deep, so Java changes are staged there and copied into the original by `sync-java.cmd`, which verifies every file's SHA-256 hash (see *Synchronisation* below).

Status vocabulary used in this document:

- **Functional** — works end to end, with automated tests.
- **Partial** — works, with documented gaps.
- **UI only** — renders, with no real behaviour behind it.
- **Broken**
- **Not implemented**
- **External** — needs a third-party system that is not present.

## 1. Architecture (verified from code)

```
Browser (Thymeleaf + Alpine.js, vendored)                  Telegram client (Mini App WebView)
   │  /, /{module}, /care/*, /settings/telegram                 │  /tg  →  /mini/patient | /mini/doctor
   ▼                                                            ▼
WorkspaceController · CareController · TelegramController · HospitalApi   (+ demo session interceptor, CSRF)
   │                                   │
   ▼                                   ▼
HospitalService (interface) ── InMemoryHospitalService         TelegramAccessService ── TelegramInitDataVerifier
   ▲  (single in-memory store shared by desktop + Mini Apps)    ▲ CareNotificationBridge (generic care events → opt-in outbox)
```

- **Persistence:** none. All data lives in one synchronized in-memory store and resets on restart. The `HospitalService` interface is the boundary to replace when Oracle is added.
- **Security:** demo login only. Roles are held in the session and checked server-side by the interceptor, `CareController`, `HospitalApi` and `TelegramController`. Spring Security is used for CSRF and headers, not for authentication.
- **Legacy code:** the database-stage code under `audit/`, `config/`, `security/` and `web/` is still excluded from compilation by `pom.xml`, as before.

## 2. Domain model and relationships (verified)

| Entity | Store | Key links | Notes |
| --- | --- | --- | --- |
| Patient | `patients` | — | Duplicate name + date-of-birth check on registration. |
| Appointment | `appointments` | patient, doctor, department, `encounter` (after check-in), `previousEncounter` (follow-ups) | Lifecycle kept separate from the visit (cf. FHIR Appointment vs Encounter). |
| Visit / encounter | `visits` | patient, doctor, `appointment` | One active visit per patient per day. Status: Waiting → In consultation → Completed / Cancelled. |
| Queue ticket | `queues` | `encounter`, doctor, `station`, `fromQueue` | Status: Waiting / Called / In service / On hold / Missed / Completed / Transferred / Cancelled. A transfer ends one ticket and opens the next. |
| Triage, notes, diagnoses, prescription drafts, certificates, procedures | `careDocuments` | patient, `encounter`, doctor | Drafts → signed/issued. Amendments reference the original. |
| Lab and imaging orders | `laboratory`, `imaging` | `encounter`, patient | Order → … → Approved (or Cancelled). Doctor review and patient release are separate flags. |
| Prescriptions (submitted) | `pharmacy` | `encounter` | Prescribing and dispensing statuses are separate. |
| Invoice | `billing` | `encounter`, patient | Draft is created at discharge, then issued by the cashier. |
| Check-in events | `checkIns` | appointment, patient, encounter, method | Records reception desk, ticket scan, arrival QR, or patient code. |
| Telegram links | `TelegramAccessService` | Telegram user → (role, patient/doctor ID) | In memory. Created only by redeeming a one-time staff-issued code. |

The 50-step journey test and the 31-step QR test confirm that every encounter-scoped record carries the same patient and encounter IDs. They also confirm that desktop and Mini App views read the same records.

## 3. Module inventory

Roles: **Adm** administrators · **Doc** doctor · **Nur** nurse · **Rec** receptionist · **Lab** laboratory staff · **Pha** pharmacist · **Cas** cashier · **Pat** patient. "View" means the role can see the module; "Write" means the server lets it change records.

| Module | Routes | Server | View | Write | Status | Problems found / fixed this round |
| --- | --- | --- | --- | --- | --- | --- |
| Dashboard | `/` | WorkspaceController, `HospitalApi.dashboard` | All staff | — | Functional | Already role-filtered. |
| Patients | `/patients[/id]`, `/care/patients/{id}` | HospitalApi, CareController | Staff | Adm, Doc, Nur, Rec | Functional | — |
| **Appointments** | `/appointments`, **`/care/appointments/{id}`** (new) | HospitalApi, CareController.journey | Staff | Adm, Doc, Rec | Functional | **Details were a thin drawer** (status chips only, no next step). Replaced by an appointment workspace: live journey, next action, medical record, role- and state-aware actions, check-in history. List rows now open it; the drawer remains for editing. |
| Doctors / Departments | `/doctors`, `/departments` | HospitalApi | Staff | Adm | Functional | — |
| Facilities | `/facilities` (+ branches, wards, rooms…) | HospitalApi | Adm, Nur | Adm | Functional | — |
| OPD registration (walk-in) | `/opd` | HospitalApi.transition → `beginVisit` | Adm, Nur, Rec | Adm, Nur, Rec | Functional | Walk-ins now enter the **Triage** station, not "Registration". |
| Patient visits | `/visits` | HospitalApi, InMemory.transferVisit | Adm, Doc, Nur, Rec | Adm, Doc | Functional | — |
| **Queues** | `/queues`, **`/care/queue`** (redesigned) | CareController, InMemory.callNext | Adm, Doc, Nur, Rec, **Lab, Pha, Cas** (new) | Same (Lab/Pha/Cas own stations only) | Functional | **(1)** After check-in, tickets stayed at "Registration" forever, so the doctor's Call Next never saw new arrivals. They now go to Triage, and vitals move them to Consultation. **(2)** The old queue page listed finished tickets first and had no actions. The new board has a Now-serving panel, Call Next, Call, Skip (back of the queue), Hold/Resume, Mark Missed, Start, Complete Step, Transfer, and per-station worklists including Lab, Pharmacy and Payment. |
| Doctor workspace | `/care/patients/{p}/encounters/{v}` | CareController.action | Adm, Doc, Nur | Clinical actions: Adm, Doc. Triage: + Nur. | Functional | Added the live journey strip, a next-step banner and `?tab=` deep links. Removed the duplicated static stage list. |
| Triage & vitals | workspace tab | careAction `triage` | Adm, Doc, Nur | Adm, Doc, Nur | Functional | — |
| Notes / diagnoses / amendments | workspace tabs | careAction `note/sign/diagnosis` | Adm, Doc, Nur | Adm, Doc | Functional | — |
| Laboratory | `/laboratory`, workspace | HospitalApi, careAction `order/cancel-order/doctor-review/release` | Adm, Doc, Lab | Lab (workflow), Doc (order/cancel/review) | Functional | — |
| Imaging | `/imaging`, workspace | same | Adm, Doc, Lab | Lab, Doc | Partial | No in-workspace report-template picker. No PACS/DICOM (external). |
| Prescriptions & pharmacy | `/pharmacy`, workspace | careAction `prescription-draft/submit-prescription`, `dispense` | Adm, Doc, Pha | Doc (prescribe), Pha (dispense) | Functional | — |
| Medical certificates | workspace tab, doctor Mini App | careAction `certificate/issue` | Adm, Doc (+ Pat: issued only) | Adm, Doc | Functional | Doctor Mini App can now preview and issue drafts (clinician confirmation required). |
| Billing / payments / insurance | `/billing`, `/payments`, `/insurance` | HospitalApi | Adm, Cas | Adm, Cas | Functional | The encounter's draft invoice appears in the journey ("Pharmacy & billing"). |
| Medicines / stock / procurement | `/medicines`, `/stock`, `/procurement` | HospitalApi | Adm, Pha | Adm, Pha | Functional | — |
| Staff, access, settings, reports, notifications, audit | `/staff` … `/audit` | HospitalApi | Adm | Adm | Functional (demo) | The permission matrix is a browser preference, not real access control (documented). |
| **Check-in desk** (new) | `/care/checkin` | CareController.scan/arrival/history | Adm, Rec, Nur | same | Functional | New. Scan or type a ticket code, expected-today list, arrival poster, Telegram link codes, history. |
| **Telegram settings** (new) | `/settings/telegram` | TelegramController | Adm (Rec read-only via API) | Adm | Functional (demo) / External (live) | New. Configuration, live status, doctor/patient/arrival QR codes, posters, doctor link codes, linked accounts, outbox. |
| **Mini App entry** (new) | `/tg` | TelegramController | Public | — | Functional (demo) / External (live) | Verifies raw `initData`. Asks for a link code if the account isn't linked. Explains demo mode. |
| Patient Mini App | `/mini/patient` | CareController.mobile/booking/arrival-checkin | Pat | Pat (own records only) | Functional (demo) | Rebuilt. Home, guided booking, appointments (reschedule/cancel/progress), ticket QR, arrival-poster scan + manual code, live queue position, released records + download, profile. |
| Doctor Mini App | `/mini/doctor` | CareController.mobile + queue/action APIs | Doc, Adm | Doc | Functional (demo) | Rebuilt. Today stats, current patient, queue actions (call/start/skip/hold/resume/complete/transfer), schedule, clinical tasks, certificates, patient summary. |

## 4. Problems discovered this round (all fixed unless stated)

1. **Doctor's queue never received new arrivals.** Check-in parked tickets at "Registration". *Fixed:* tickets go to Triage, vitals move them to Consultation, and Call Next covers everyone not yet seen.
2. **Appointment details had no workflow.** They showed status chips only, with no medical record and no next step. *Fixed:* new appointment workspace.
3. **The queue page put finished tickets first and had no queue actions.** *Fixed:* new queue board.
4. **Lab, pharmacy and cashier had no queue view.** *Fixed:* station worklists, with server checks restricting them to their own stations.
5. **Patients could create a check-in ticket they could never use** (e.g. while another visit was active), and only discovered this at the desk. *Fixed:* eligibility is checked when the ticket is issued.
6. **Patient self check-in did not prove presence.** The old flow let a patient generate a code and confirm arrival with that same code from anywhere. *Fixed:* self check-in now requires today's **hospital arrival code** (poster at reception, rotatable). Alternatively, reception scans the patient's single-use ticket.
7. **No Telegram sign-in, account linking, deep links, QR posters or notifications.** *Fixed (demo verified; live needs external setup):* see `docs/telegram-mini-app-setup.md`.
8. **The doctor identity was hard-coded** (`DOC-0001`) in scope checks and mobile data. *Fixed:* it is now read from the session (`demoDoctor`), so a Telegram-linked doctor is scoped to their own patients.
9. **The Mini App doctor home crashed with a script error** when no patient was being served. *Fixed.*
10. **QR codes rendered as tiny 18 px icons** because of a global Lucide `svg` rule. *Fixed* with explicit sizing.
11. **Translation gaps:** 300+ new or never-translated strings, including four older CSV import errors, dynamic journey details and status words. *Fixed:* catalog check reports 0 missing; dynamic details use pattern rules.
12. **Khmer text rendered in a monospace font** inside the appointment header. *Fixed.*

## 5. Workflow references (concepts applied, not code copied)

- **Visit vs encounter (OpenMRS/Bahmni):** a visit is the period a patient interacts with the facility and contains encounters, the individual clinical transactions. This prototype collapses them into one "encounter/visit" per outpatient attendance. Splitting them is listed for the Oracle phase.
- **Appointment vs encounter (FHIR):** FHIR uses Appointment for planned activity and Encounter for what actually happened. Its status values cover planned, arrived, triaged and in-progress. Here the appointment keeps its own status while arrival, triage and consultation are derived from the encounter and queue. The journey steps mirror those FHIR concepts. **No FHIR API is implemented or claimed.**
- **Service queues (OpenMRS O3):** queues organise patients by service and status (Waiting / In service), sorted by time waited, with explicit transfers to the next service. The board here follows the same model: per-station worklists, priority then arrival time, transfer opens the next ticket, and queue actions don't change the appointment.

## 6. Synchronisation (why `D:\ehospital-review` exists)

The desktop file bridge can only write up to 7 folders deep. Every Java file sits 8–9 folders deep (`src\main\java\com\example\e_hospital\management\prototype\…`). Therefore:

1. Templates, JavaScript, CSS, locales, fonts, scripts and docs are written **directly into the original project**.
2. Java sources and tests are written to `D:\ehospital-review\main\…` and `D:\ehospital-review\test\…`.
3. `sync-java.cmd` (in the original project root) copies them into the correct packages and **verifies every file's SHA-256** against `java-sync-manifest.txt`. It stops with an error on any mismatch. It does not touch any other file.

Until `sync-java.cmd` has been run, the original project contains the new UI but the **old Java**. The new pages call APIs that don't exist yet, so run the sync before starting the application.
