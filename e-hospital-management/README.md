# CarePlus E-Hospital Management

The existing Spring Boot / Thymeleaf / Tailwind / Alpine application now includes a connected clinical workspace and doctor/patient mobile previews. All records are synthetic, shared in memory, and reset on restart. No database is connected.

## Run locally

**After receiving Java changes in `D:\ehospital-review`**, run `sync-java.cmd` once from this folder (Command Prompt). It verifies the SHA-256 of every staged Java file, copies them into `src\main\java\…\prototype` and `src\test\java\…\prototype`, verifies again and runs the tests. See [docs/upgrade-report.md](docs/upgrade-report.md).

Use JDK 26 (the current Maven target):

```powershell
.\mvnw.cmd '-Dmaven.repo.local=.cache/m2' -o spring-boot:run
```

Open http://127.0.0.1:8090/login. On a machine without the prepared cache, use `.\mvnw.cmd spring-boot:run` with Maven Central access.

Choose a demo staff role or Patient. Patient identities PAT-0001 and PAT-0018 are available for local previews; PAT-0018 starts without an active encounter. The doctor identity in the focused workspace is DOC-0001. This is a synthetic login with server-side demo capabilities, not real hospital authentication.

## Main workflows

- Register/search a patient → book an appointment → **appointment workspace** `/care/appointments/{id}` (live journey, next step, medical record, actions) → Check In.
- Reception: **Check-In Desk** `/care/checkin`: scan or type the patient's ticket code, check in from today's list, print the daily arrival QR, issue Telegram link codes.
- Queues: `/care/queue` (Now serving, Call Next, Skip, Hold/Resume, Missed, Transfer, per-station worklists incl. Laboratory, Pharmacy and Payment).
- Telegram: **Settings → Telegram Mini App** `/settings/telegram` (QR codes and posters). Telegram entry: `/tg`. Setup guide: [docs/telegram-mini-app-setup.md](docs/telegram-mini-app-setup.md).
- Doctor: `/care/queue` → call/start consultation → encounter workspace.
- Unified chart: `/care/patients/{patientId}`; visit: `/care/patients/{patientId}/encounters/{visitId}`.
- Desktop and mobile use the same HospitalService. Mobile previews: `/mini/doctor` and `/mini/patient` (patient role required).
- English/Khmer and Light/Dark/System controls are in the header. Only preferences are stored in browser localStorage.
- Clinical notes and prescriptions support drafts. Finalized notes require an amendment; prescriptions enter the pharmacy worklist only after review/submission.
- Laboratory/imaging worklists retain their existing staged transitions. Doctor review and patient release are explicit encounter actions.
- Certificates require manual clinician statements and issuance review. Print/PDF uses the browser and a visible demo watermark.

## Validate

```powershell
.\mvnw.cmd '-Dmaven.repo.local=.cache/m2' -o test
npm run test:i18n
npm run test:care
npm run test:browser
npm run test:journey
npm run test:qr
npm run css
```

Browser tests require a running server and Playwright Chromium. Set `PLAYWRIGHT_CHROMIUM_EXECUTABLE` where needed; `HOSPITAL_URL` overrides the local address. Screenshots and JSON reports are written to `test-results/`. `test:journey` runs the full Registration → Discharge workflow across roles, then sweeps Khmer, dark mode and 390 px layouts.

Read [implementation report](docs/upgrade-report.md), [gap analysis](docs/upgrade-gap-analysis.md), and [existing modules guide](CAREPLUS.md). Earlier database instructions are preserved in [historical database documentation](docs/historical-database-readme.md); they do not describe the active application.

Telegram `initData` signatures are verified server-side when `CAREPLUS_TELEGRAM_BOT_TOKEN` is set (never commit the token); account linking still fails closed. Noto Sans Khmer is bundled under `static/ui/fonts` so Khmer renders offline.

Oracle, real hospital identity, bot credentials, Telegram account linking, PACS/DICOM, and external message/payment integrations are not connected. Live Telegram linking fails closed. Check-in currently uses an opaque, five-minute, single-use code; camera QR scanning is not implemented. Use synthetic information only.
