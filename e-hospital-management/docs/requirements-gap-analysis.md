# Requirements gap analysis

Reviewed 2026-11-11 in the original checkout. Primary evidence: `E_Hospital_Management_Client_Requirements_v1_1.pdf`, all 10 physical pages. Extracted text: `requirements/pdf-extracted.txt`; all pages rendered and visually inspected in `requirements/rendered/`. The core has sections 1–16; physical pages 9–10 contain addenda A/B. The prompt's references to sections 19/20 are not PDF section numbers.

The repository already contained extensive uncommitted clinical/UI work at the start. Preserve it. Earlier reports about a transfer folder, absence of Git, and production completeness are historical claims, not evidence for this audit. Active source is `src/main/java/.../prototype`; Maven excludes the older `audit`, `config`, `security`, and `web` packages. All active data is synthetic and in memory. No Oracle integration is authorized.

| Requirement | Current implementation / source | Problem found | Priority | Proposed fix / disposition |
|---|---|---|---|---|
| PDF §4 director scope | `DemoAccess`, `NavigationAdvice`, `WorkspaceController`, service dashboard | Director role absent | P0 | Add read-only leadership role, navigation, and operational metrics |
| §4 radiology role | Laboratory staff also handle imaging | Separate imaging role absent | P0 | Add imaging worklist, dashboard and scoped queue |
| §8 departmental queues | `CareController.callNext/action/mobile` | Department staff can call another station; mobile feed includes every station | P0 | Enforce station capabilities server-side and filter feed |
| §12 encounter integrity | `InMemoryHospitalService.save` | Existing order could move to another encounter for the same patient | P0 | Reject encounter reassignment before mutation |
| §3 optional steps | `careAction(start)` | Starting consultation changes unrelated lab/pharmacy tickets into consultation tickets | P0 | Restrict to active consultation/triage tickets; preserve optional department queues |
| A4 linked roles | `HospitalApi.role`, `WorkspaceController` | Browser role switch accepts changes for Telegram-linked staff | P0 | Reject linked-session role changes and all role switching in integration mode |
| §11/B2 authorized mobile | `CareController.mobile/portal` | Doctor mobile API bypasses integration-mode session requirement | P0 | Require verified linked session in integration mode; restrict doctor portal role |
| §6.3 completion blockers | `care.html`, `care.js` | Blockers appear only after failed completion | P0 | Display pending tests, review tasks and draft notes with direct links |
| §10 modern UI | existing `design.css`, shared shell, dashboard, appointment, care, queue | Substantial redesign already present; claims need rerun | P0 | Preserve, add actionable readiness panel and bot configuration; capture actual before/after |
| A3 bot commands | `TelegramAccessService` | No webhook or command handler | P1 | Add private-chat `/start`, `/help`, `/language`, `/contact`, web-app buttons, secret validation, replay/rate limits |
| B3 bot health | `telegram.html/js` | No command preview or webhook receipt history | P1 | Add admin-only English/Khmer preview, readiness and last receipt; clearly distinguish receipt from delivery |
| A3 hospital contact | Telegram settings | No configurable public contact/hours | P1 | Add public contact field used by `/contact`; no invented address |
| §15 doctor scheduling | `availableSlots`, doctors, staff leave catalog | Fixed weekday slot grid; leave/location calendars do not drive booking | P0 | Implemented dated sessions/breaks/intervals/location/closure, shared slots, version checks and booking-conflict rejection. Staff leave approvals, recurring rosters and room conflicts remain partial |
| §12 pharmacy | dispense + FEFO stock | No authorized returns workflow | P1 | Partial; retain existing stock validation and document returns gap |
| §19–21 admission/wards/emergency | facilities/wards catalogs, urgent queue priority | No admission lifecycle, ward observations or distinct emergency disposition | P1 | Missing connected expansion pathways; catalogs and priority alone are insufficient |
| §22/27/28 theatre/equipment/infection | no active workflow | Missing | P2 | Keep in traceability; do not create disconnected placeholder screens |
| §30 incidents | audit events only | No incident/complaint/corrective-action workflow | P1 | Missing; audit is not a substitute |
| §16/33 access/audit | demo session capabilities, in-memory audit; excluded historical auth | No production identity, durable audit, backups/restore/monitoring | P0 / production phase | Explicit demonstration boundary; no production readiness claim |
| A3/B3 reminders | opt-in generic event bridge and in-memory outbox | No timed reminder scheduler, retries or durable delivery confirmation | P1 | Event notifications partial; real delivery requires credentials/hosting and additional infrastructure |
| §10/12 localization/themes | bundled Noto Sans Khmer, semantic CSS, catalogs | Catalog completeness does not prove translation quality or device usability | P0 | Add translations for changes; run language/theme browser sweeps; native-speaker review remains |
| B4 live phone QR/Telegram | local web preview, signature verifier, account codes, camera/manual scanner | No supplied credentials/public HTTPS/physical phone | External | Verify local workflows; report live acceptance as blocked by external integration |

Baseline verification: 55 active Java tests pass; locale catalog check passes (2,508 keys). Before captures: 28 real browser views, zero JS errors/failed application requests/viewport overflow. These results describe the starting checkout, not proof that all PDF requirements were complete.

Implementation and final verification evidence is recorded in `requirements-implementation-report.md` and `requirements-traceability.md`.
