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
