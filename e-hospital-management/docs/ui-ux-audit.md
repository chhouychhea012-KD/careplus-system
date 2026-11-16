# CarePlus UI/UX audit (round 3)

Date: 2026-11-11 · Scope: every staff page, both Mini Apps, English/Khmer, light/dark, desktop (1440 px) and phone (390 px).
Method: the running application (`java -jar …`, port 8090) was opened in headless Chromium for the Doctor, Receptionist, Nurse and Administrator roles and the Patient Mini App; screenshots are in `docs/screenshots/ui-before/`. Controllers, templates, fragments, CSS and JavaScript were then read for the causes.

Priority: **P1** = blocks or misleads daily work · **P2** = slows people down or looks broken · **P3** = polish.

## Findings

| # | Area | Problem | Priority | Cause |
|---|------|---------|----------|-------|
| 1 | Navigation | Two different page shells. Module pages use a sidebar, while appointment, queue, check-in, Telegram settings and the patient workspace use a separate top bar with 4–6 links. Users lose their place when moving between them. | P1 | `fragments/layout.html` vs `fragments/clinic.html :: top` |
| 2 | Navigation | The sidebar repeats itself: "Quick access" links (My Queue, My Appointments…) sit above full module groups that link to the same places (Queue management, Appointments…). The doctor menu has 20+ entries. | P1 | Quick-access block + every permitted module |
| 3 | Navigation | Nav labels are 8 px and links 11 px (10 px under 1200 px wide). Khmer labels at that size are unreadable and uppercase letter-spacing breaks Khmer shaping. | P1 | `app.css` `.nav-label`, `.nav-link` |
| 4 | Dashboard | Every role gets the same hospital-wide overview (totals, trends, a decorative banner). A doctor cannot see their queue, current patient or pending notes, and a receptionist cannot see expected arrivals. | P1 | One `dashboard` view in `workspace.html` |
| 5 | Dashboard | The "Viewing as" select shows *Administrator* even when signed in as Doctor. The `<option>`s are rendered by `x-for` after `x-model` has already applied the value. | P2 | `workspace.html` demo bar |
| 6 | Appointment details | "Allergies: No known allergies" is shown as a **red alert pill**, the same style as a real allergy. | P1 | `appointment.html` shows any non-empty allergy text as an alert |
| 7 | Appointment details | Before check-in, clinical roles see no clinical actions at all (not even "Open patient record"). | P2 | `clinicalActions` returns `[]` without an encounter |
| 8 | Doctor workspace | 14 tabs wrap onto two rows. Their order mixes history, orders and documents. | P2 | `care.js` `tabs` + `care.css` wrap |
| 9 | Doctor workspace | "Medical Certificates" has no preview, so the doctor only sees the result after Print Preview. The template selector is the last field in the form. | P2 | Generic action form |
| 10 | Doctor workspace | The deep link `?tab=certificates` (used in docs and links) is ignored; only `?tab=cert` works. | P3 | `care.js` deep-link map |
| 11 | Queue | Status counts only show statuses that are present, and they can't be clicked. There is no department filter. Transferred, Completed and Missed patients are hidden in a collapsed history. | P2 | `queue.html` `counts` |
| 12 | Queue | Skip and Mark Missed act immediately with no confirmation. A missed patient cannot be resumed from the list. | P2 | `queue.html` buttons |
| 13 | Queue | Department shows "—" for tickets that have none recorded (`— · Dr. Sarah Wilson`). The Call Next button uses an emoji icon. | P3 | Row template |
| 14 | Tables | Module tables cannot be sorted. Paging is server-side, so sorting only the visible page would be misleading. | P2 | `table.html`, `HospitalService.list` |
| 15 | Tables | The appointment "stage" column prints `— · —` when there is no queue ticket. | P3 | `table.html` |
| 16 | Localization | Placeholders "Search patient or ticket" (queue) and "Search appointments…" (module tables) stay English in Khmer. | P2 | Missing key; attribute translation lacked the "Search X…" rule |
| 17 | Localization | Eyebrows, nav labels and the brand tagline use uppercase + letter-spacing, which splits Khmer clusters. | P2 | `app.css` |
| 18 | Mobile | On phones the language/theme selects squeeze into the top bar ("Englis", "Lig"), and tiles stack one per row, so the page is very long. | P2 | `topbar-right` layout, `minmax(200px)` |
| 19 | Mobile (Mini Apps) | The bottom bar uses Unicode glyphs (⌂ # ▤ ✓ ❏) and 👤, which render differently on each phone. The doctor Home has only four counters and one button: no next patient and no schedule. | P2 | `mini.js` `tabs`, `mini.html` |
| 20 | Theme | Dark tokens differ between `app.css` (green-grey) and `care.css` (navy), so module pages and clinical pages look like two products in dark mode. | P2 | Two `.theme-dark` palettes |
| 21 | Build / cache | `spring.web.resources.cache.period=1h`, so after an upgrade browsers keep the old CSS/JS for up to an hour ("the UI didn't change"). | P1 | `application.properties` |
| 22 | Certificates | Doctors have no list of the certificates they issued, and no way to start one except opening each encounter. | P2 | No page |
| 23 | Tasks | The doctor's "Tasks" link opens the Mini App, not a desktop page. | P3 | Quick-access link |

## Checked and working (no change needed)

- Server-side role enforcement for every API (`WorkspaceController` interceptor, `CareController.allow`); the menu change does not affect it.
- Unsaved-change warnings: module dialogs (`dirty`) and the patient workspace (`beforeunload`).
- Destructive module actions already ask for confirmation (Delete, Cancel appointment, Mark no-show).
- The theme follows the operating system live (`matchMedia` change listener in `i18n.js`).
- The patient journey and queue states come from service data (round 2).
