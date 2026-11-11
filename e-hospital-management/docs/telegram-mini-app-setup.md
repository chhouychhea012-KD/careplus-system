# Telegram Mini App setup — CarePlus

This guide connects the CarePlus doctor and patient Mini Apps to a real Telegram bot, so that staff and patients can scan a printed QR code with their phone and open CarePlus inside Telegram.

> **Status of this build**
> - **Demo mode** (default): verified locally by automated browser tests.
> - **Integration mode:** verified by server-side tests that use synthetically signed Telegram data.
> - **Not tested:** a real bot, a real phone, or a public HTTPS server. Those need your bot token and hosting, which were not available here.

## 1. How access works (and why a QR code alone grants nothing)

```
Poster QR  ──►  https://t.me/<bot>[/<app>]?startapp=patient|doctor|arrive-CODE
                 └─ Telegram opens  https://<your-domain>/tg  inside the app
/tg  ──►  POST /api/telegram/session  { initData: <raw signed string> }
          server: HMAC-SHA256 check with the bot token · auth_date ≤ 15 min old · replay check
          ├─ Telegram user already linked → session as that patient/doctor → /mini/patient or /mini/doctor
          └─ not linked → "Link your hospital account" → POST /api/telegram/link { code }
                          one-time code issued by reception (patients) or an administrator (doctors), 10 min, single use
```

- **What the server trusts:** only the raw `initData` string. It is checked with Telegram's first-party scheme: every field except `hash`, sorted and joined with newlines, signed with `HMAC_SHA256(key="WebAppData", bot_token)`. `initDataUnsafe` is never trusted.
- **Doctor QR:** scanning it does **not** make anyone a doctor. The doctor role comes only from a link code created by an administrator for a specific doctor record.
- **Patient scope:** linked patients see only their own appointments, queue tickets, released results, prescriptions and issued certificates (the same scoping as the demo patient).
- **Session hygiene:** the session ID is rotated at sign-in. The bot token is read from the environment and is never sent to browsers or shown on the settings page.

## 2. Create the bot and Mini App (BotFather)

1. In Telegram, open **@BotFather** and send `/newbot`. Choose a name and a username ending in `bot`, e.g. `CarePlusHospitalBot`. Keep the **token** secret.
2. Register the Mini App:
   - **Main Mini App** (recommended): `/mybots` → your bot → **Bot Settings → Configure Mini App → Enable Mini App**. Set the URL to `https://<your-domain>/tg`. Links look like `https://t.me/CarePlusHospitalBot?startapp=patient`.
   - **or a named Mini App:** `/newapp` → choose the bot → title, description, photo → URL `https://<your-domain>/tg` → short name, e.g. `care`. Links look like `https://t.me/CarePlusHospitalBot/care?startapp=patient`.
3. Optional: set the bot's menu button to open the same URL.

## 3. Host CarePlus at a public HTTPS address

Telegram only opens Mini Apps over **HTTPS**, and phones must be able to reach the server. Options:

- **For a pilot:** a reverse proxy with a real certificate (nginx/Caddy + Let's Encrypt) in front of `java -jar …` or `mvnw spring-boot:run`.
- **For short tests from your PC:** a tunnel such as Cloudflare Tunnel or ngrok that gives you an `https://…` URL forwarding to `http://127.0.0.1:8090`.

CarePlus binds to `127.0.0.1` by default (`server.address` in `application.properties`). That works behind a reverse proxy or tunnel on the same machine. If the proxy runs elsewhere, set `server.address=0.0.0.0` and firewall the port.

## 4. Configure CarePlus

Set these **before** starting the application. In Windows Command Prompt:

```bat
set CAREPLUS_TELEGRAM_BOT_TOKEN=123456789:AA...your token...
set CAREPLUS_MODE=integration
mvnw.cmd -Dmaven.repo.local=.cache/m2 -o spring-boot:run
```

| Property / environment variable | Default | Meaning |
| --- | --- | --- |
| `careplus.telegram.bot-token` / `CAREPLUS_TELEGRAM_BOT_TOKEN` | empty | Verifies `initData`. Empty = Telegram sign-in disabled. |
| `careplus.mode` / `CAREPLUS_MODE` | `demo` | `integration` refuses synthetic patient logins and requires Telegram sign-in for `/mini/*`. |
| `careplus.telegram.init-data-max-age-minutes` | `15` | Maximum age of `auth_date`. |
| `careplus.telegram.send-enabled` / `CAREPLUS_TELEGRAM_SEND_ENABLED` | `false` | When `true`, opt-in reminders are sent through the Bot API `sendMessage`. Otherwise they stay in the outbox as "Queued — sending disabled". |

Then sign in as **Administrator** → **Telegram Mini App** (`/settings/telegram`):

1. Enter the **bot username**, the optional **short name**, and the **public HTTPS URL** (the same address you gave BotFather, without `/tg`). Save.
2. The status should read **Ready for live Telegram testing**. If not, the page lists exactly what is missing.
3. The **Patient**, **Doctor** and **Hospital arrival** QR codes now encode `t.me` links. Use **Print poster** for A4 posters (always black on white, with Khmer text).

## 5. Linking accounts

- **Patient:** at reception, after checking ID, open **Check-In Desk → Link a patient's Telegram**, choose the patient and read out the 8-character code. The patient scans the patient QR (or any CarePlus link), opens the app, and enters the code once.
- **Doctor:** an administrator opens **Telegram Mini App → Link a doctor's Telegram**, chooses the doctor record and gives the code to the doctor in person.
- **Unlinking:** **Telegram Mini App → Linked Telegram accounts → Unlink**.

Links are stored **in memory** in this prototype and are lost on restart. See §9.

## 6. QR codes in this system

| QR | Encodes | Purpose | Contains patient data? |
| --- | --- | --- | --- |
| Patient access | `t.me/<bot>[/<app>]?startapp=patient` | Opens the patient Mini App | No |
| Doctor access | `…?startapp=doctor` | Opens the doctor Mini App (role still requires a link) | No |
| Hospital arrival (daily) | `…?startapp=arrive-<8-char code>` | Patient self check-in at the hospital (proves presence) | No. The code changes daily and can be rotated at the Check-In Desk. |
| Appointment ticket (on the patient's phone) | `CPCI:<random UUID>` | Reception scans it to check the patient in | No. Server-stored, single use, expires in 5 minutes. |

Check-in rules, enforced on the server:

- the appointment must belong to the signed-in patient and be scheduled for today;
- only one active visit per patient per day;
- a ticket works once (replays are rejected);
- a patient cannot redeem their own ticket, only reception can;
- self check-in requires today's arrival code.

## 7. Camera scanning

The reception desk and the patient Mini App can scan QR codes with the device camera. They use the browser `BarcodeDetector` API where available and fall back to the bundled jsQR library. Browsers only allow camera access on **HTTPS or localhost**, so scanning works through your HTTPS deployment or on `http://127.0.0.1`. It does not work from a phone opening `http://<pc-ip>:8090`.

**Fallback when scanning isn't possible:** every QR has a typed alternative. The arrival code is printed under the poster QR. Reception can type a ticket code, check the patient in from **Expected today**, or search by name.

## 8. Notifications (opt-in)

Linked users can enable reminders in **Profile**. Events: appointment booked, queue called, visit completed, follow-up booked. Messages are generic, for example:

> *CarePlus: queue number Q-1007 has been called. Open the CarePlus Mini App to see where to go.*

They never include diagnoses, results, prescriptions, documents or patient IDs. Messages are recorded in the outbox at **Telegram Mini App → Notification outbox**. Real sending happens only with `careplus.telegram.send-enabled=true` and a bot token, and is **not verified** in this build.

## 9. Before production use (not done in this prototype)

- **Persistence:** move Telegram links, link codes and check-in tokens to the database (Oracle phase) with audit trails.
- **Real staff authentication:** the desktop login is still a synthetic demo login. Admins who issue doctor codes must be genuinely authenticated.
- **Consent and privacy:** consent records for Telegram contact, a retention policy, and a documented lawful basis for processing health data.
- **Security hardening:** rate limiting on `/api/telegram/*`; monitoring; secret storage for the token; HTTPS-only cookies (`server.servlet.session.cookie.secure=true`).
- **Real-device testing:** iOS and Android Telegram clients, light/dark themes, camera permissions, and slow networks.

## 10. Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| `/tg` shows "Telegram is not connected" | Opened outside Telegram, or no bot token configured (demo mode). |
| "This Telegram launch was already used" | The same `initData` was replayed from another session. Close and reopen the Mini App. |
| "Telegram data has expired" | `auth_date` is older than 15 minutes. Reopen from Telegram, and check the server clock. |
| QR opens `http://127.0.0.1…` | No bot username is configured. These are demo links that only work on the same PC. |
| Camera button errors | The page is not on HTTPS/localhost, or camera permission was denied. Use the typed code. |
