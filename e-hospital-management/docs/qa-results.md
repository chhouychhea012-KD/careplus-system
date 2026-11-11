# QA results — 11 November 2026 (round 2)

All results below come from runs of the **actual application** built from this source. The environment was Linux, JDK 21 and Maven 3.9.11. The project targets Java 26, so it was compiled here with `-Djava.version=21` and `pom.xml` was not changed. Browser tests used Chrome 147 via Playwright 1.61.1, with external network requests blocked, so the bundled assets were proven to work offline. Each browser suite ran against a **freshly started** server.

## 1. Automated test results

| Suite | Command | Result |
| --- | --- | --- |
| Java unit + MVC | `mvnw test` | **47 / 47 pass** (6 classes) |
| – HospitalWorkflowTests (existing) | | 13 / 13 |
| – ConnectedCareTests (existing) | | 11 / 11 |
| – WorkflowIntegrityTests (round 1) | | 7 / 7 |
| – TelegramVerificationTests (round 1) | | 3 / 3 |
| – **JourneyAndCheckInTests** (new) | derived journey · Call Next priority · skip/hold/resume · single-use ticket + replay · arrival code + rotation · opt-in notifications without clinical detail | 7 / 7 |
| – **TelegramIntegrationModeTests** (new) | signed `initData` → link required → one-time code → scoped session · replay from another session rejected · forged data rejected · doctor link scoping · arrival start parameter · synthetic patient login refused in integration mode · settings admin-only, validated, token never exposed · patient cannot redeem own ticket | 6 / 6 |
| Translation catalog | `npm run test:i18n` | 2,386 keys · 1,104 declared phrases · **0 missing · 0 invalid** |
| Module browser suite (existing, updated) | `npm run test:browser` | **264 assertions pass**, 0 runtime errors |
| Care browser suite (existing, updated) | `npm run test:care` | **32 assertions pass**, 0 errors |
| End-to-end journey (round 1, extended) | `npm run test:journey` | **56 assertions pass**, 0 errors |
| **QR + Mini App end to end** (new) | `npm run test:qr` | **31 assertions pass**, 0 errors |

### What the end-to-end suites prove

- **Journey** (`journey-test.cjs`):
  1. Receptionist registers the patient, books, checks in (lands on the queue).
  2. Nurse records vitals.
  3. Doctor: the patient appears in the queue; starts the consultation; signs a note; orders a lab test and an imaging study, then cancels the imaging order with a reason; issues a medical leave certificate; books a follow-up.
  4. Discharge is blocked while the lab result is pending.
  5. Lab staff collect, process, enter, review and approve the result.
  6. Doctor reviews and releases it, then completes the visit.
  7. Cashier sees the draft invoice.
  8. Every linked record carries the same patient and encounter IDs, and the follow-up points back to the originating encounter.
  9. A generic queue Transfer moves the patient into the receiving doctor's queue.
  10. Khmer + dark sweep of 11 pages; 390 px overflow check on 11 pages.
- **QR + Mini App** (`qr-mini-test.cjs`):
  1. Reception shows a scannable daily arrival QR. It encodes a Mini App start parameter, never patient data.
  2. A phone opens that link. Demo mode explains why Telegram isn't connected, sends the user to sign-in with a return path, and lands them in the check-in sheet with the code prefilled. Confirming arrival issues a queue ticket.
  3. The desktop appointment workspace shows the same ticket number, the check-in channel, and check-in as done.
  4. A second patient is refused a ticket while another visit is active. After that visit is closed, they show their ticket QR; reception redeems it once; a replay is rejected; the phone switches to the queue automatically.
  5. The doctor Mini App calls the patient, and the patient's phone shows "It's your turn."
  6. Telegram settings render three QR codes, reject an invalid bot username, and produce `https://t.me/CarePlusDemoBot?startapp=patient|doctor` links. No token material appears in the page.
  7. Appointment workspace and queue have no horizontal overflow at 768 px and 390 px.

## 2. Language, theme and layout checks

| Check | Method | Result |
| --- | --- | --- |
| English ↔ Khmer switching | Journey + care suites switch language; the care suite verifies an unsaved clinical draft survives the switch | Pass |
| Khmer coverage on rendered pages | Text-node sweep of 11 pages in Khmer | Only data remains in English (names, department/test names, IDs, dates, units). Dynamic journey details are translated by pattern rules. |
| Khmer typography offline | Bundled Noto Sans Khmer, checked visually with external fonts blocked | Correct shaping (the "before" run showed broken glyphs without it) |
| Khmer dates | `toLocaleDateString('km-KH')` | **Not verifiable here:** the test Chrome build has no Khmer locale data. Windows Chrome and Edge include it. |
| Dark mode | Sweep for large light surfaces on 11 pages | 0, except the deliberately white QR quiet zones |
| Theme persistence | Care suite reload test | Pass |
| Print output | A4 certificate and poster use fixed dark-on-white styles | Checked in code; not printed on paper |
| Desktop 1440 px / tablet 768 px / phone 390 px | Screenshots + `scrollWidth` checks | 0 px overflow on every checked page |
| Keyboard focus | Global `:focus-visible` outline (existing); dialogs close on Escape | Spot-checked, not audited with assistive technology |

## 3. Screenshots (before → after)

Captured with the same script on a fresh server. The "before" run had no checked-in patient, so its workspace shots were skipped. The "after" run seeds one realistic checked-in, triaged patient.

| Screen | Before | After |
| --- | --- | --- |
| Dashboard | `screenshots/before/01-dashboard.png` | `screenshots/after/01-dashboard.png` |
| Appointment list | `before/02-appointment-list.png` | `after/02-appointment-list.png` |
| **Appointment details** | `before/03-appointment-details.png` (drawer) | `after/03-appointment-details.png` (workspace) |
| Doctor workspace | — | `after/04-doctor-workspace.png` |
| **Doctor queue** | `before/05-doctor-queue.png` | `after/05-doctor-queue.png` |
| Check-in desk (new) | — | `after/16-checkin-desk.png`, `after/qr-checkin-desk.png` |
| Medical records / lab / imaging | `before/06–08` | `after/06–08` |
| Medical certificate | — | `after/09-certificate.png` |
| Telegram settings (new) | `before/10-telegram-setup.png` (did not exist) | `after/10-telegram-setup.png`, `after/qr-telegram-settings.png` |
| Doctor Mini App | `before/11-mini-doctor.png` | `after/11-mini-doctor.png`, `after/qr-doctor-queue.png` |
| Patient Mini App | `before/12-mini-patient.png` | `after/12-mini-patient.png`, `after/qr-patient-queue.png` |
| Patient QR check-in | `before/13-patient-checkin.png` | `after/13-patient-checkin.png`, `after/qr-patient-ticket.png` |
| Khmer + dark | `before/14-dashboard-km-dark.png` | `after/14-…`, `after/15-workspace-km-dark.png`, `after/17-appointment-km-dark.png`, `after/18-mini-patient-km-dark.png` |
| Tablet / phone | — | `after/qr-appointment-768.png`, `after/qr-queue-390.png` |

## 4. Not tested (requires something unavailable here)

- **A real Telegram bot, a real phone, or a public HTTPS deployment.** Integration mode is verified only with synthetically signed `initData` in MockMvc.
- **Live camera scanning.** The scanner code path (BarcodeDetector/jsQR) was not exercised with a physical camera. Every QR flow was verified through its typed-code path, which feeds the same server endpoints.
- **Real Telegram message delivery** (`careplus.telegram.send-enabled=true`).
- **Java 26 build of this round.** It was compiled and tested on Java 21 here; your earlier run of round 1 on Java 26 passed. Run `sync-java.cmd` (it runs the tests) to confirm on your machine.
- **Screen readers and formal WCAG contrast measurements.**
