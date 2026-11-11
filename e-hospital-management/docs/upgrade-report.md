# E-Hospital upgrade report — round 2 (11 November 2026)

This round audited the whole application again and rebuilt the screens that made daily work hard: appointment details, the queue, check-in, and the doctor and patient Mini Apps. It also added a real, security-checked path for opening the Mini Apps from a QR code in Telegram.

All data is synthetic, and the system is a prototype that is not ready for patient care. Oracle is not integrated.

Related documents:
- [full-system-audit.md](full-system-audit.md): module inventory, architecture, problems found
- [qa-results.md](qa-results.md): every test run, screenshots
- [telegram-mini-app-setup.md](telegram-mini-app-setup.md): BotFather, hosting, configuration
- [upgrade-gap-analysis.md](upgrade-gap-analysis.md): the original audit

## How to run the updated original application

Java sources live 8–9 folders deep, and the desktop file bridge cannot write that deep. So the Java part of this round is staged in `D:\ehospital-review` and copied in by a verified script. **Run it once from the original project, in Command Prompt:**

```bat
cd /d "D:\e-hospital management\e-hospital-management"
sync-java.cmd
```

The script:
1. checks the SHA-256 of all 22 staged Java files;
2. copies them into the correct packages;
3. re-checks the copies;
4. runs `mvnw test` offline.

It stops with an error if anything doesn't match. Then start the application:

```bat
mvnw.cmd -Dmaven.repo.local=.cache/m2 -o spring-boot:run
```

Open http://127.0.0.1:8090. Templates, JavaScript, CSS, translations, fonts, test scripts and documentation were written directly into the original project.

## 1. Fully implemented and tested

| Area | What changed | Evidence |
| --- | --- | --- |
| **Appointment workspace** (`/care/appointments/{id}`) | Replaces the drawer. Contents: sticky patient header (age/sex, allergies, conditions, doctor, department, visit number, queue ticket and position), a **patient journey derived from live state** (Registration → Check-In → Triage → Consultation → Orders & results → Pharmacy & billing → Discharge), and a **next-step banner**. The medical record has tabs (summary, visits, notes, diagnoses, lab, imaging, prescriptions, certificates). Clinical and administrative actions depend on role and state. Check-in history is shown. Appointment list rows open it. | JourneyAndCheckInTests, browser + QR suites, screenshots |
| **Doctor workspace** | Adds the same live journey and next-step banner, plus deep links (`?tab=note|lab|imaging|rx|cert|followup|triage`) used by the appointment page and Mini Apps. Drafts are kept per tab, as before. | Journey + care suites |
| **Queue fix (workflow bug)** | Checked-in patients used to stay at "Registration", so **the doctor's Call Next never saw them**. Now: check-in → Triage, vitals → Consultation, and the doctor's queue covers everyone not yet seen. | JourneyAndCheckInTests |
| **Queue board** (`/care/queue`) | Now-serving panel, Call Next (priority, then arrival), Call, Skip (back of the queue), Hold/Resume, Mark Missed, Start Consultation or Start Service, Complete Step, Transfer (station + receiving doctor), per-station worklists (Consultation, Triage, Laboratory, Imaging, Pharmacy, Payment, Doctor review), counts, today's history. Queue completion ≠ visit completion. | Java + browser + QR suites |
| **Lab/pharmacy/cashier queues** | These roles can open their own station worklists. The server stops them changing consultation queues. | ConnectedCare + QR suites |
| **Check-in desk** (`/care/checkin`) | Scan (camera) or type the patient's ticket code; expected-today list with one-click check-in; daily hospital arrival QR with print and rotate; patient Telegram link codes; check-in history. | QR suite |
| **Secure check-in** | Two channels: (a) patient shows a single-use, 5-minute, server-stored ticket (`CPCI:<uuid>`, no personal data) and **reception redeems it**; (b) patient scans the **daily hospital arrival code** (proves presence). Eligibility is checked when the ticket is issued. Replays are rejected. One active visit per patient per day. Patients can no longer confirm their own ticket. Every check-in records its channel. | JourneyAndCheckInTests, TelegramIntegrationModeTests, QR suite |
| **Patient Mini App** (rebuilt) | Phone-first with a bottom tab bar. Home (queue ticket, position, next appointment, quick actions, hospital info); guided booking (branch → department → doctor → slot → reason → review); appointments (upcoming/history, reschedule, cancel, visit progress); check-in sheet (arrival scan or code, or show ticket QR); live queue with called/missed/held instructions; released records with certificate download; profile with notification opt-in. | care + QR suites, screenshots |
| **Doctor Mini App** (rebuilt) | Today stats, current patient, call next; queue with call/start/skip/hold/resume/complete/transfer; schedule; clinical tasks (draft notes, results to review, pending orders, open consultations, follow-ups); certificates (preview, review & issue); patient summary. | QR suite |
| **Telegram settings** (`/settings/telegram`) | Bot username, short name and public URL with validation; live status listing exactly what is missing; Patient, Doctor and Hospital-arrival QR codes encoding `t.me` deep links (or clearly labelled demo links); printable A4 posters (English + Khmer); doctor link codes; linked accounts; notification outbox. | TelegramIntegrationModeTests, QR suite |
| **Telegram sign-in (server side)** | `/tg` sends the raw `initData`. The server verifies the HMAC, a 15-minute freshness window and replay across sessions. An unlinked user must redeem a one-time staff-issued code. The session is created only for the linked role and identity, with session ID rotation. In integration mode, synthetic patient logins and demo Mini App access are refused. The token comes only from the environment. | TelegramIntegrationModeTests (6) |
| **Notifications** | Opt-in, generic messages (booking, queue call, visit completed, follow-up) without clinical details or IDs; outbox shown to admins. | JourneyAndCheckInTests |
| **Khmer/English** | 300+ strings added (new screens, server messages, four older CSV errors, journey details, statuses), plus pattern rules for dynamic details. 0 missing in the catalog check. Patient-entered text is never translated. | i18n check, Khmer sweep |
| **Themes** | All new screens use the semantic tokens; 0 light surfaces in the dark sweep (QR quiet zones excepted on purpose); posters and print are always dark-on-white. | Journey sweep |
| **Responsive** | 0 px horizontal overflow at 390 px on 11 pages, and at 768 px on the appointment and queue pages. | Journey + QR suites |

## 2. Implemented but not verified

- **Camera QR scanning** (BarcodeDetector, falling back to bundled jsQR). Needs HTTPS or localhost and a physical camera. Every flow was verified through its typed-code path, which uses the same endpoints.
- **Real Telegram message delivery** (`careplus.telegram.send-enabled=true`). Not exercised.
- **Java 26 build of this round.** Tested on Java 21; `sync-java.cmd` runs the tests on your Java 26 machine.

## 3. Demo-only

- Desktop login is a synthetic role picker (no passwords). The fixed demo doctor is `DOC-0001`; demo patients are `PAT-0001` and `PAT-0018`.
- All data, Telegram links, link codes, check-in tokens and the outbox are in memory and reset on restart.
- Without a configured bot, QR codes encode local `/tg` preview links, clearly labelled as demo.

## 4. Requires external setup

A Telegram bot (BotFather), a public HTTPS address, the token in `CAREPLUS_TELEGRAM_BOT_TOKEN`, and `careplus.mode=integration`. See [telegram-mini-app-setup.md](telegram-mini-app-setup.md). **Real-device testing has not been done.**

## 5. Not completed

- Real staff authentication and authorization (an identity provider, passwords, per-doctor patient assignment beyond the demo scope check).
- Persistent storage (Oracle), audit of read access, consent records, data retention.
- Imaging report template picker in the workspace; PACS/DICOM.
- Structured diagnosis codes (ICD); medication and allergy reconciliation.
- Separate visit vs encounter entities (the prototype uses one record per outpatient attendance).
- Formal accessibility audit (screen readers, contrast measurements).

## 6. Exact file changes this round

**Java (via `D:\ehospital-review` → `sync-java.cmd`):**
- `service/HospitalService.java` — new journey, call-next, check-in and arrival operations
- `service/InMemoryHospitalService.java` — derived journey; Call Next with priority; skip/resume semantics; Triage → Consultation station flow; ticket redemption; daily arrival code; check-in history; ticket eligibility; care events
- `service/TelegramAccessService.java` (new) — settings, deep links, link codes, linked accounts, `initData` replay guard, notification outbox, optional sender
- `service/CareNotificationBridge.java` (new)
- `controller/CareController.java` — appointment/queue/check-in pages and APIs; session-based doctor identity; integration-mode Mini App guard; lab/pharmacy/cashier station queues; patients can't self-redeem tickets
- `controller/TelegramController.java` (new) — settings page/API, `/tg`, session, link, notifications
- `controller/WorkspaceController.java` — return-to path after login (`/mini/*` only); integration mode refuses synthetic patients; public Telegram endpoints
- Tests (new): `JourneyAndCheckInTests.java`, `TelegramIntegrationModeTests.java`

**Templates:**
- New: `ui/appointment.html`, `ui/queue.html`, `ui/checkin.html`, `ui/telegram.html`, `ui/tg.html`, `ui/fragments/clinic.html`
- Rewritten: `ui/mini.html`
- Updated: `ui/care.html`, `ui/login.html`, `ui/fragments/dialogs.html`, `ui/fragments/table.html`

**Static files:**
- New: `ui/clinic.css`, `ui/clinic.js`, `ui/appointment.js`, `ui/queue.js`, `ui/checkin.js`, `ui/telegram.js`, `ui/tg.js`
- Rewritten: `ui/mini.js`
- Updated: `ui/care.js`, `ui/i18n.js`, `ui/locales/en.json`, `ui/locales/km.json`
- Vendored: `ui/vendor/qrcode.js` (MIT), `ui/vendor/jsqr.js` + `jsqr-LICENSE.txt` (Apache-2.0)

**Scripts:**
- New: `scripts/qr-mini-test.cjs`
- Updated: `scripts/journey-test.cjs`, `scripts/browser-test.cjs`, `scripts/care-browser-test.cjs`, `scripts/vendor-assets.cjs`

**Build and configuration:**
- `package.json` (QR libraries, `test:qr`), `package-lock.json`
- `README.md`
- `sync-java.cmd`, `sync-java.ps1`, `java-sync-manifest.txt` (new)

**Documentation:**
- `docs/full-system-audit.md`, `docs/telegram-mini-app-setup.md`, `docs/qa-results.md` (new)
- `docs/upgrade-report.md` (this file)
- `docs/screenshots/before|after/*.png`

No Maven dependencies were added, so the offline build keeps working. Nothing was deleted from the project.

## 7. Round 1 summary (earlier today)

- Lab and imaging orders can be cancelled, so a visit is no longer stuck forever.
- Checked-in appointments can't be cancelled.
- Queue and visit transfers keep the patient in the system.
- Visit cancellation cascades to its queue tickets.
- A draft encounter invoice is created at discharge.
- Server-side `initData` verifier added.
- A4 print layout.
- Accessibility labels added.
- Role quick-access links added.
- 80 Khmer strings added and Noto Sans Khmer bundled.
- `.gitignore` fix (`/data/`).
