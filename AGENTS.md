# Agents Guide (Copilot/AI Assistants)

This document helps AI coding assistants (GitHub Copilot, ChatGPT, etc.) work effectively in this repository. It sets constraints, context, and preferred patterns so that changes are safe, minimal, and aligned with the existing architecture.

## Goals
- Preserve behavior: do not add, remove, or change features unless explicitly requested.
- Prefer small, surgical edits over large refactors, unless explicitly requested.
- Match existing style and patterns; avoid introducing new frameworks.
- Keep the codebase simpler after each change (fewer branches, clearer names, less duplication).
- Update or add lightweight documentation when it clarifies intent.

## Tech Stack Snapshot
- Language: Kotlin (JVM target 17)
- UI: Jetpack Compose (Material 3)
- DI: Koin
- Networking: Retrofit + Moshi + OkHttp (with `AuthInterceptor` and logging)
- Persistence: Room (KSP) with entities/DAOs and `AppDatabase`
- Background work: WorkManager
- Image loading: Coil
- HTML parsing: Jsoup
- Coroutines: kotlinx-coroutines

Android config: `compileSdk=36`, `minSdk=29`. Build uses Gradle Kotlin DSL.

## Project Structure (high level)
- `app/src/main/java/com/hiosdra/hreader`
  - `data/`
    - `remote/` Retrofit API (`MinifluxApiService`), repository wrapper (`MinifluxApiRepository`), DTOs
    - `local/` Room `AppDatabase`, `dao/`, `entity/`, repositories
    - `ai/` OpenRouter client and model selection
    - `preferences/` `PreferencesManager`
    - `paywall/` `PaywallBypassService`
  - `ui/`
    - `main/`, `article/`, `feeds/`, `settings/`, `components/`, `theme/`
  - `navigation/` Compose navigation routes
  - `di/` Koin modules (`appModule`, `networkModule`)
  - `util/` helpers (e.g., `BionicReadingProcessor`, `SyncPerformanceLogger`)
  - `worker/` WorkManager workers and setup

## How To Work In This Repo (for agents)
1. Understand context first
   - Skim the relevant package(s) and any adjacent ViewModels/Repositories.
   - Respect existing layering:
     - UI (Compose) -> ViewModel -> Repository -> DAO/API.
   - Keep DI registrations in `di/appModule.kt` or `di/networkModule.kt` when adding classes.

2. Propose a small plan before editing
   - Outline 2–5 concise steps (parse files, refactor method X, update DI, run build).
   - Execute the plan incrementally; keep exactly one step in progress.

3. Keep edits minimal and behavior‑preserving
   - Do NOT change public contracts (function names/params) unless required by a bug fix.
   - Avoid moving many files at once; prefer local, focused changes.
   - Don’t introduce new dependencies without explicit need and approval.

4. Build and verify
   - Build: `./gradlew build`
   - Unit tests: `./gradlew test`
   - Android tests (if any): `./gradlew connectedAndroidTest` (requires a device/emulator)
   - Only add tests when there is an existing pattern/place for them.

5. Style and patterns
   - Kotlin: prefer `val` over `var`, expression functions when clear, early returns, and small private helpers.
   - Coroutines: keep suspend boundaries in repositories; use `viewModelScope` in ViewModels.
   - Compose:
     - Keep composables small and stateless where possible; hoist state.
     - Use `remember`/`rememberSaveable` appropriately; avoid heavy work in composition.
     - Follow Material 3 via `Theme.kt`; don’t bypass theme colors/typography without reason.
   - Room: if you touch schema, bump version and add a migration. Do not introduce destructive fallbacks.
   - DI: register new singletons/viewModels/workers in the appropriate Koin module.
   - Logging: use Android `Log` sparingly; avoid leaking secrets or tokens.

6. Networking and security
   - Use the existing Retrofit/Moshi/OkHttp stack; add endpoints via `MinifluxApiService` and wrap in `MinifluxApiRepository`.
   - Do not log request/response bodies that may contain secrets.
   - Use `BuildConfig` values already defined; do not hardcode credentials in code or docs.

## Cleanup Guidance (no feature changes)
When simplifying code, prefer these transformations:
- Remove unused imports, dead code, and redundant `else` branches.
- Convert trivial `var` to `val`; minimize mutable state.
- Replace nested `if` with `when` where it reads clearer.
- Extract small pure helpers to reduce duplication.
- Narrow visibility (make functions `private`/`internal` when possible).
- Prefer early returns over deep nesting.
- Keep composable parameters stable; lift lambda creation out of hot recomposition paths when practical.

Avoid:
- Renaming public APIs or routes unless clearly internal and unused.
- Changing threading behavior or coroutine scopes.
- Switching libraries or architectural patterns.
- Reformatting entire files without a code change.

## Where Things Live (quick map)
- Remote API: `data/remote/MinifluxApiService.kt`, wrapped by `MinifluxApiRepository.kt` (adds retries).
- Database: `data/local/AppDatabase.kt` with DAOs in `data/local/dao/` and entities in `data/local/entity/`.
- Repositories: `data/local/repository/` combine DAOs with remote to provide models to the app.
- ViewModels: `ui/*/*ViewModel.kt` expose UI state via `StateFlow`.
- UI Screens: `ui/*/*Screen.kt` (Compose Material 3).
- DI: `di/appModule.kt`, `di/networkModule.kt` for wiring.
- Workers: `worker/*` and setup in `MyApplication.kt`/`WorkManagerSetup.kt`.

## Adding or Modifying Code (patterns)
- New network call: define Retrofit interface method -> add wrapper in `MinifluxApiRepository` (with retries) -> inject via Koin -> call from repository/viewModel.
- New DB table/column: add `@Entity`/field -> DAO methods -> bump DB version and add migration -> wire via repository.
- New screen: create composable + ViewModel -> add navigation route -> inject via Koin.

## Testing Notes
- Unit tests should prefer repository/viewmodel boundaries.
- Avoid real network; abstract via repositories or inject fakes.
- Keep tests deterministic; limit reliance on time and randomness.

## Review Checklist (for agents)
- Kept behavior identical? (Yes/No)
- Build passes locally? (`./gradlew build`)
- DI updated consistently? (modules match new types)
- No new dependencies without reason?
- No secrets logged or duplicated in code/docs?
- Changes are minimal, targeted, and documented when non-obvious?

## Useful Commands
- Build all: `./gradlew build`
- Run unit tests: `./gradlew test`
- Assemble debug APK: `./gradlew :app:assembleDebug`

If you’re unsure, prefer to ask for clarification or submit the smallest viable change.

