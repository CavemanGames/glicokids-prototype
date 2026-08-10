# GlicoKids: Gamified Diabetes Management (MVP Prototype)

## 1. Project Overview
**GlicoKids** is a native Android application designed for the gamified management of Type 1 Diabetes in children. It serves as an intelligent assistant that simplifies daily carbohydrate counting through visual plate estimation. Beyond insulin dosage calculation, the app features a gamification ecosystem designed to encourage physical activity, promoting overall health and reducing sedentary screen time.

## 2. Problem Statement
Managing Type 1 Diabetes in childhood requires constant mathematical calculations for carbohydrates and insulin dosages at every meal. Errors in this process can lead to severe hypoglycemia or hyperglycemia. GlicoKids alleviates the mental burden on parents and boosts child engagement by offering safe calculations and transforming care routines into a rewarding RPG-style adventure.

## 3. Technology Stack
- **Language**: Kotlin
- **UI Framework**: XML with ViewBinding (Material 3)
- **Architecture**: MVVM with Clean Architecture
- **Dependency Injection**: Hilt
- **Persistence**: `SharedPreferences` + `EncryptedSharedPreferences` (AES-256) + hand-written `SQLiteOpenHelper` (no Room)
- **Navigation**: Navigation Component & Intents
- **Device Communication**: `SmsManager` (native SMS), `Intent.ACTION_SENDTO` (`smsto:` and `mailto:`), `BroadcastReceiver` (incoming SMS), `NotificationManager`

## 4. User Experience (UX)
*   **Child Interface (Primary UI)**: Playful, colorful, and reward-focused. Features a central "Meal Mission" (photo capture), achievement panels (badges and XP), and daily challenges.
*   **Parent Interface (Admin Panel)**: Password/PIN protected area where clinical parameters (Sensitivity Factor, Carb Ratio, Target Glucose) are configured and history is reviewed.

## 5. Design & UX Architecture
The design follows Google's Material Design guidelines, focused on cognitive accessibility for children. 

### Wireframe Flow
![Project Wireframes](docs/prancha-fluxo.png)

*The interface transitions between a dark "Space Station" theme for children and a clean, sober theme for parents.*

## 6. Technical Roadmap & Development History

### Phase 1: Infrastructure & Navigation (Module 2)
- **Core Architecture**: Establishment of Clean Architecture layers (Data, Domain, Presentation).
- **Navigation Strategy**: Implementation of Activity-to-Activity flows via `Intents` and `Extras` for data passing.
- **Security Baseline**: Mandatory `AlertDialog` for medical disclaimers on startup.
- **Lifecycle Management**: Detailed logging and management of `onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, and `onDestroy`.

### Phase 2: Data Capture & Business Logic (Module 3)
- **Structured UI**: Responsive layouts using `ScrollView` as root to ensure keyboard compatibility.
- **Data Capture**: Specialized `EditText` components with specific `inputType` (Decimal/Numeric) for safe data entry.
- **Resilient Calculation**: Implementation of the "Hero's Bolus" logic: `(carbs/15) + (glucose-100)/50`, rounded down for safety.
- **Interactive States**: Use of `ViewBinding` for real-time validation and dynamic result display.
- **Design System Integration**: Application of the "Space Station" design tokens (Colors, 3D Buttons, Glass Cards).

### Phase 3: Advanced UI & Interaction (Module 4)
- **General Interface Hardening**: Implementation of a hybrid design using **ConstraintLayout** for flat hierarchies and **ScrollView** (`fillViewport="true"`) for responsive input handling.
- **Dynamic Image Systems**:
    - **GridView Gallery**: Structural skeleton for the Achievement Gallery using a 3-column matrix and `BaseAdapter`.
    - **ImageSwitcher**: Core engine for the Avatar Station with sequential transitions and native slide animations.
    - **ImageView Management**: High-contrast rendering for mascot states and meal photo binding.
- **Multi-Paradigm Menus**:
    - **Global Options Menu**: XML-driven routing via `MaterialToolbar` for administrative access.
    - **Contextual Menus**: Long-press activation on `GridView` items for targeted object actions.
- **WebView Integration**: Secure institutional guidelines rendering with JavaScript and optimized viewport scaling.
- **Architectural Refinement**:
    - **Helper Pattern**: Centralization of global visual behaviors in a reusable `UIHelper` object.
    - **Hardened Security**: AES-256 GCM encryption for user preferences and PINs via `EncryptedSharedPreferences`.
    - **Enterprise Foundation**: Full migration to **MVVM with Clean Architecture** and **Hilt DI**.
- **Enhanced Identity**: Custom vector medals and rarity classification system.
- **Configurable Health Logic**: User-defined glucose targets with validation and persistent encryption.

### Phase 4: Persistence (Module 5)

Three storage layers, each with a single owner — no screen decides on its own where data goes.

| Layer | Class | What it stores | Why |
|---|---|---|---|
| `EncryptedSharedPreferences` | `EncryptedStorage` (`glicokids_secure_prefs`) | `parent_pin` | the only sensitive value; AES-256 GCM/SIV |
| `SharedPreferences` | `AppPreferences` (`glicokids_prefs`) | `child_name`, `avatar_index`, `range_min/max`, `target_glucose`, `isf`, `ic_ratio`, `max_dose`, `xp`, `coins`, `streak`, `onboarding_done`, `last_report_at` | non-sensitive settings read across Activities |
| SQLite | `GlicoKidsDbHelper` (`glicokids.db` v1) | `glucose_readings`, `meals`, `medals`, `foods` | historical series, queried and aggregated |

**Room is deliberately absent** — the academic requirement is a hand-written `SQLiteOpenHelper`, so the Room dependencies were removed from the build.

Requirement → implementation:

| # | Requirement | Where |
|---|---|---|
| 1 | `SharedPreferences` | `AppPreferences` via `getSharedPreferences("glicokids_prefs", MODE_PRIVATE)` |
| 2 | Sharing between Activities | avatar picked in `AvatarActivity` is read by `KidsDashboardFragment`; target range edited in `ParentAreaFragment` recolors `GlucoseLogActivity` and the home |
| 3 | `FileOutputStream` | `ReportStorage.generateAndSave()` → `filesDir/relatorio_glicokids.txt` |
| 4 | `FileInputStream` + `InputStreamReader` | `ReportStorage.readReport()`, shown in a scrollable dialog by "Ver ›" |
| 5 | External storage | `ReportStorage.saveReportExternally()` — `Environment.getExternalStorageDirectory()` up to API 28, `getExternalFilesDir(DIRECTORY_DOCUMENTS)` from 29; single fail-safe function inside `try/catch`, returns the path used or `null` |
| 6 | `openRawResource` | seeds the `foods` table from `res/raw/alimentos.json`; `HelpActivity` falls back to `res/raw/ajuda_offline.html` when offline |
| 7 | `SQLiteOpenHelper` | `GlicoKidsDbHelper` — 4 tables, seeds `foods` and the 6 medals on `onCreate` |

Report rules: generated from the last 7 days of SQLite data, never carries the child's full name (first name + initials), and the external copy requires an explicit confirmation dialog — health data leaving the app sandbox (LGPD). All I/O runs off the main thread through `viewModelScope` / `lifecycleScope` with `Dispatchers.IO`.

Glucose colouring has one source of truth, `UIHelper.glucoseStatus(value, min, max)`, fed by the configured range — there is no hardcoded 70 or 180 anywhere outside the defaults in `AppPreferences`.

### Phase 5 (Planned)
- **Onboarding Flow**: Responsible party registration (LGPD compliant).
- **Hero Profile**: Child's profile customization and initial clinical setup.
- **Secure Authentication**: Google Login integration and PIN creation/recovery system.

### Phase 6: Device Communication (Module 6)

The support network — the people a family already trusts to help — needed a channel that
works the moment a child's glucose leaves the target range, without waiting for anyone to
open the app.

| # | Requirement | API | Screen |
|---|---|---|---|
| 1 | Native SMS | `SmsManager.sendTextMessage()` | Glucose Alert screen — a sensor reading below target range messages every contact configured to receive alerts |
| 2 | SMS via the messaging app | `Intent.ACTION_SENDTO` (`smsto:`) with a pre-filled body | Support Network screen — "Share 7-day summary" |
| 3 | Incoming SMS + notification | `BroadcastReceiver` (`SMS_RECEIVED`) + `NotificationChannel`/`NotificationManager` | Received Messages screen |
| 4 | Email | `Intent.ACTION_SENDTO` (`mailto:`) with recipient, subject, and body pre-filled | Parent Area — "Send by email", recipients are contacts opted into the report |

**Support Network in SQLite.** Unlike single-value settings, the support network is a list of
people, each with their own permissions — it lives in its own `contacts` table (`glicokids.db`,
schema v2) rather than in `SharedPreferences`. `relationship` is a required field (mother,
father, grandmother, grandfather, aunt/uncle, caregiver, doctor, school, other) — it is what
lets a message recipient understand who is speaking. The contact registered during onboarding
is inserted as the primary contact and cannot be removed, only edited; every other contact is
managed through a read-only list plus a validated dialog, never inline.

**Manual entries never trigger an automatic alert.** The app tracks how a glucose reading was
captured — typed by the child (`MANUAL`) or reported by a sensor (`SENSOR`) — and only the
sensor path can fire an SMS on its own. If the child typed the value, they are awake, aware,
and already interacting with the app; automating a message on top of that would be noise, not
safety. A single named rule owns this decision, so no screen re-implements it independently.

**Hypoglycemia and hyperglycemia are configured separately**, each with its own automatic,
suggest, or off mode, because they carry different clinical urgency: a low reading can precede
loss of consciousness and defaults to sending automatically, while a high reading is safer to
default to a one-tap confirmation before anyone is notified.

**Privacy.** Messages carry the child's first name and last-name initial only, matching the
report generated in Module 5 — never the full name. Consent is implicit in registering a
contact, with an explicit notice shown at that point.

**Accessibility and discoverability.** No action exists behind a long-press alone — every
context menu also has a visible "⋮" button offering the same action, keeping the destructive
and detail actions reachable without a gesture some users cannot perform. All interactive
targets, including switches, meet a 48dp minimum touch area. Every icon that carries meaning
has a `contentDescription` that states its current state (for example, "Ana, mother, receives
SMS alerts"); purely decorative icons are marked as not important for accessibility. Glucose
status is always paired with a text label, never communicated by color alone. The full flow
from the Support Network screen through the alert and the received-message screen is navigable
with TalkBack. A contrast check on this pass also caught `text_muted_light`, a token that fell
short of the 4.5:1 minimum against every light background it was used on; it is corrected and
now held in place by an automated test.

Meal photo capture opens the device's own camera app
(`ActivityResultContracts.TakePicturePreview()`); the photo is shown to the child in memory and
is never written to disk. Carbohydrate values remain entered or confirmed by the user — the
image does not feed the dose calculation. A planned evolution is to estimate carbohydrates from
that photo using AI, to support children who are still learning to calculate them on their own;
because an estimated value can be wrong, it will always be shown for confirmation before it
feeds the dose calculation — the same confirmation step the meal flow already requires today.

The default SMS-capable line configured on the device is the one Android's `SmsManager` uses to
send; on a dual-SIM phone, that setting — not the app — decides which line the message goes out
on.

**What on-device testing found.** The automated suite stayed green throughout the module — 201 tests by the end —
the defects below lived in UI wiring a JVM test suite has no way to reach, and surfaced only once
the app ran on a physical phone. Two of the four requirements needed a fix once tried for real:
sharing the 7-day summary opened the phone's contacts screen instead of the messaging app,
because the phone number was never passed to the intent; the email button had no listener wired
to it at all, and once wired, moved from a generic `ACTION_SEND` to the `mailto:` intent used
above. Sending an SMS now also waits for the system's actual delivery result, with a timeout,
before the alert screen reports success — earlier it reported "SMS sent" immediately after the
call, with no confirmation the message had gone anywhere. On a physical device, one test message
did not arrive, and the alert screen correctly reported that it could not confirm delivery rather
than claiming success; the cause of the non-delivery was not determined. Three defects found this
way carried clinical weight: a button that offered sugar during a hyperglycemia reading, a home
screen that showed a fixed, reassuring number regardless of the last reading actually taken, and
the SMS-confirmation issue above. All are fixed. This is the strongest argument in the module for
treating on-device verification as its own required step, not an optional pass after the test
suite is green.

Emulator note: the Android emulator does not deliver SMS to a real phone number — a call to
`sendTextMessage()` returning without an exception proves the call was made correctly (also
covered by a `ShadowSmsManager` unit test), not that a message was received. Incoming SMS, by
contrast, is testable end to end through the emulator's Extended Controls → Phone → Incoming
SMS panel. Requirement 3 was verified end to end on a physical device: a message sent from a
second line was captured by the receiver, matched against the support network, and displayed with
the sender's name and relationship rather than a bare number.

**SMS requires RCS to be turned off.** Modern messaging apps default to RCS, which travels over
data and is end-to-end encrypted. RCS messages do not fire the `SMS_RECEIVED` broadcast — the only
one Android exposes to an app that is not the device's default messaging app — so an incoming
message never reaches the receiver, and an outgoing `SmsManager` call can be rerouted without
confirmation. Both directions started working the moment chat features were disabled in the
messaging app. This is a property of the transport, not of this app: any application that depends
on SMS behaves the same way.

## 7. Quality Assurance & DevOps
- **Gitflow Strategy**: Professional branch structure (`main`, `staging`, `develop`).
- **CI/CD Pipeline**: GitHub Actions configured for automated build validation and JUnit testing on every Pull Request.
- **Branch Protection**: Strict rules and bypass lists implemented to ensure code integrity.

## 8. Test Credentials (Prototype Only)
To evaluate the prototype, use the following mocked credentials:
- **Parent Area PIN**: `1234`
- **Simulated Child Name**: `Lucas`

## 9. Copyright & Licensing

Copyright © 2026 Luiz Augusto Melo. All rights reserved.

This project is an academic prototype. No license to use, modify or distribute is granted. The source code is made available for academic evaluation purposes only.

*Copyright © 2026 Luiz Augusto Melo. Todos os direitos reservados. Este projeto é um protótipo acadêmico. Nenhuma licença de uso, modificação ou distribuição é concedida. O código-fonte está disponível apenas para fins de avaliação acadêmica.*

---
*This project is a technical prototype developed for academic purposes.*
