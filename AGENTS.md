# Repository Guidelines

This guide helps contributors and AI assistants make safe, minimal, and behavior‑preserving changes to hReader.

## Project Structure & Module Organization
- App code: `app/src/main/java/com/hiosdra/hreader/`
  - Data: `data/remote` (Retrofit/Moshi), `data/local` (Room), `data/ai`, `preferences`, `paywall`.
  - UI: `ui/main`, `ui/article`, `ui/feeds`, `ui/settings`, `ui/components`, `ui/theme`.
  - Infra: `navigation`, `di`, `util`, `worker`.
- Tests: `app/src/test/` (unit). Assets/resources under `app/src/main/res/`.
- Android: Kotlin/JVM 17, `compileSdk=36`, `minSdk=29`.

## Build, Test, and Development Commands
- `./gradlew build`: Compile, run checks and unit tests.
- `./gradlew test`: Run unit tests only.
- `./gradlew :app:assembleDebug`: Produce a debuggable APK.

## Coding Style & Naming Conventions
- Kotlin: prefer `val` over `var`, small focused functions, early returns, and private/internal visibility by default.
- Compose (Material 3): keep composables small and mostly stateless; hoist state; use `remember`/`rememberSaveable` appropriately; follow `ui/theme/Theme.kt` for colors/typography.
- Naming: Classes/objects `PascalCase`; functions/vars `camelCase`; constants `UPPER_SNAKE_CASE`; files match the top-level type.
- Persistence/Networking: add Room changes with migrations; add network calls via `MinifluxApiService` and wrap in `MinifluxApiRepository`.

## Testing Guidelines
- Prefer testing at repository/viewmodel boundaries; keep tests deterministic and avoid real network (inject fakes).
- Naming: mirror target class (e.g., `FooRepositoryTest`); test names describe behavior (e.g., `fetch_returnsCachedOnError`).
- Run with `./gradlew test`. Add tests only where a clear pattern exists.

## Commit & Pull Request Guidelines
- Commits: small, scoped, imperative mood (e.g., "Fix sync retry backoff"). Avoid unrelated changes.
- PRs: include summary, rationale, linked issues, and screenshots/GIFs for UI. Call out DI changes and DB migrations. Avoid new dependencies without prior discussion.
- Preserve behavior unless a change is explicitly requested.

## Security & Configuration Tips
- Never hardcode credentials or tokens; use existing `BuildConfig` values. Avoid logging sensitive request/response bodies.
- Reuse the existing stack: Retrofit+Moshi+OkHttp (with `AuthInterceptor`), Koin DI, Room (no destructive fallbacks), WorkManager for background work.

## Agent-Specific Instructions
- Respect layering: UI (Compose) → ViewModel → Repository → DAO/API.
- Keep edits minimal and localized; update DI in `di/appModule.kt` or `di/networkModule.kt` when adding types.
- Validate with `./gradlew build` before requesting review.

