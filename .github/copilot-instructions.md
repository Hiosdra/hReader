# GitHub Copilot Instructions for hReader

**ALWAYS follow these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.**

hReader is an Android RSS/Feed reader app built with Kotlin, Jetpack Compose, and MVVM architecture. It connects to a Miniflux backend API to synchronize feeds and articles.

## Quick Start & Build System

### Prerequisites
- Java 17 (already available in environment)
- Android SDK (automatically downloaded by Gradle)
- No additional SDK installation required

### Bootstrap and Build
```bash
cd /home/runner/work/hReader/hReader
```

**CRITICAL TIMING EXPECTATIONS:**
- **NEVER CANCEL BUILDS**: Fresh/clean builds take up to 9+ minutes. Always set timeouts to 60+ minutes.
- Cached builds: 3+ minutes (after dependencies downloaded)
- Unit tests: 20 seconds (set 5+ minute timeout)
- Lint: 17 seconds (set 5+ minute timeout)  
- APK assembly: 17 seconds (set 5+ minute timeout)

### Essential Commands
```bash
# Full clean build - NEVER CANCEL, takes 9+ minutes, set 60+ minute timeout
./gradlew clean build --no-daemon

# Run unit tests - takes 20 seconds, set 5+ minute timeout
./gradlew test --no-daemon

# Run lint checks - takes 17 seconds, set 5+ minute timeout  
./gradlew lint --no-daemon

# Build debug APK - takes 17 seconds, set 5+ minute timeout
./gradlew assembleDebug --no-daemon

# Build release APK - takes 17 seconds, set 5+ minute timeout
./gradlew assembleRelease --no-daemon
```

## Validation & Testing

### Before Committing Changes
Always run these commands before committing (set appropriate timeouts):
```bash
./gradlew lint --no-daemon          # 17 seconds - NEVER CANCEL
./gradlew test --no-daemon          # 20 seconds - NEVER CANCEL  
./gradlew assembleDebug --no-daemon # 17 seconds - NEVER CANCEL
```

### Manual Testing Scenarios
Since this is an Android app, you cannot run it in the current environment. However, always validate:
1. **APK Generation**: Verify `app/build/outputs/apk/debug/app-debug.apk` is created after `assembleDebug` (~37MB file)
2. **Build Success**: Ensure all Gradle tasks complete with "BUILD SUCCESSFUL"
3. **Lint Issues**: Check that lint reports no new critical issues (reports saved to `app/build/reports/lint-results-debug.html`)
4. **Test Results**: Verify all unit tests pass (currently 9 unit tests including architectural tests)

## Architecture & Code Structure

### Key Directories
```
app/src/main/java/com/hiosdra/hreader/
├── MainActivity.kt                 # Main entry point
├── MyApplication.kt               # Application class with Koin setup
├── data/                          # Data layer
│   ├── local/                     # Room database, DAOs, entities
│   ├── remote/                    # Retrofit API, DTOs
│   └── preferences/               # SharedPreferences wrapper
├── di/                           # Dependency injection modules
│   ├── appModule.kt              # Main DI module
│   └── networkModule.kt          # Network DI module
├── ui/                           # Presentation layer (Jetpack Compose)
│   ├── article/                  # Article screens & ViewModels
│   ├── feeds/                    # Feed management screens
│   ├── main/                     # Main navigation
│   └── theme/                    # UI theming
├── worker/                       # Background sync workers
└── navigation/                   # Navigation logic
```

### MVVM Architecture
- **Model**: Room entities, API DTOs in `data/`
- **View**: Jetpack Compose screens in `ui/`  
- **ViewModel**: Koin-injected ViewModels following Android lifecycle

### Key Dependencies (Verified)
- **Kotlin**: 2.2.10
- **Android Gradle Plugin**: 8.12.1
- **Compose BOM**: 2025.08.00 (Material3, UI, Tooling use BOM-managed versions)
- **Activity Compose**: 1.10.1
- **Navigation Compose**: 2.9.3
- **Lifecycle Runtime KTX**: 2.9.2
- **Coroutines (Android)**: 1.10.2
- **Room**: 2.7.2 (runtime, ktx, ksp compiler)
- **WorkManager**: 2.10.3
- **Koin BOM**: 4.1.0 (android, compose-navigation, workmanager)
- **Retrofit**: 3.0.0 (NOTE: Upstream stable series is 2.x; ensure 3.0.0 artifacts are intended and compatible)
- **Retrofit Moshi Converter**: 3.0.0
- **OkHttp Logging Interceptor**: 5.1.0
- **Moshi**: 1.15.2 (with KSP codegen)
- **Coil Compose**: 2.7.0
- **Accompanist Pager**: 0.36.0 (DEPRECATED – plan migration to androidx.compose.foundation.pager)
- **Browser**: androidx.browser:1.9.0
- **Testing**: JUnit 4.13.2, ArchUnit 1.4.1, AndroidX Test JUnit 1.3.0, Espresso 3.7.0

Notes:
- Accompanist Pager is deprecated; migrate to the official Foundation Pager soon.
- Verify Retrofit 3.x usage; if unintentional, revert to 2.11.0 to avoid unexpected API changes.
- Compose BOM manages versions; do not hardcode individual compose artifact versions outside the BOM.

## Code Style Preferences

### Documentation
- **NO JAVADOCS**: Do not generate or include Javadoc-style comments in the code
- **NO INLINE COMMENTS**: Do not add inline comments to the code
- Use meaningful method and variable names instead of comments or documentation

### Kotlin Specific
- Follow Kotlin idioms and best practices
- Use Kotlin's concise syntax features (extension functions, smart casts, etc.)
- Prefer immutability where appropriate (use `val` over `var` when possible)
- Use java.time API for date and time operations

### Android Best Practices
- Follow Android architecture components guidelines
- Prefer Kotlin Coroutines for asynchronous operations
- Use Jetpack Compose for UI construction
- Handle sensitive data appropriately
- Don't hardcode credentials or API keys
- Don't specifically measure and log time taken for operations

## Common Development Tasks

### Adding New Features
1. Create/modify entities in `data/local/entity/` for database changes
2. Add DAOs in `data/local/dao/` for database operations
3. Create/update repositories in `data/local/repository/` for business logic
4. Add ViewModels in appropriate `ui/` subdirectory
5. Create Compose screens in matching `ui/` subdirectory
6. Update DI modules in `di/` to wire dependencies
7. Always run lint and tests before committing

### Working with Database (Room)
- Database migrations are set to `fallbackToDestructiveMigration(false)` in `appModule.kt`
- Schema is defined by entities in `data/local/entity/`
- DAOs provide database access in `data/local/dao/`

### Working with API (Retrofit)
- API services defined in `data/remote/`
- Miniflux API base URL and credentials in `build.gradle.kts` BuildConfig
- DTOs use Moshi for JSON serialization

### Background Processing (WorkManager)
- Sync workers in `worker/` directory
- Periodic sync configured in `WorkManagerSetup.kt`
- Triggered on app startup in `MyApplication.kt`

## CI/CD Pipeline
The `.github/workflows/ci.yml` runs on push/PR:
```bash
./gradlew build  # This runs the full build including tests and lint
```

## Troubleshooting

### Common Issues
- **Build fails**: Run `./gradlew clean` then rebuild
- **Dependency issues**: Check `build.gradle.kts` versions match project needs
- **Room compiler warnings**: The warning about 'annotationProcessor' vs 'kapt' in build logs is expected and can be ignored

### Build Performance
- Use `--no-daemon` flag for consistent CI-like builds
- First builds download dependencies (~9 minutes total)
- Subsequent cached builds complete in ~3 minutes  
- Individual tasks (lint, test, assemble) complete quickly when dependencies cached

### Expected Build Output Sizes
- Debug APK: ~37MB (`app/build/outputs/apk/debug/app-debug.apk`)
- Release APK: ~28MB (`app/build/outputs/apk/release/app-release-unsigned.apk`)

## Navigation & Routes (Updated)

Current key routes:
- `main` – unified article list (all feeds)
- `main?feedId={feedId}` – same screen filtered to a specific feed (Long). Uses `defaultValue = -1L` sentinel; interpret `-1L` as null.
- `feeds` – subscriptions list
- `feed/{feedId}` – feed details
- `add_feed` – add new feed
- `article/{articleIds}/{initialIndex}` – paged article reader
- `settings` – settings screen

Guidelines:
- Do NOT declare nullable Long nav arguments; use a sentinel `defaultValue` (e.g. `-1L`) and map to null in code.
- When adding optional filters, prefer query style `main?foo={foo}` with default sentinel instead of creating a new screen.
- Keep MainViewModel feed filtering via `setFeed(feedId)` / `clearFeed()`; avoid duplicating per-feed viewmodels.
- Prefer adding new list variations as parameters to MainScreen instead of new routes unless layout diverges significantly.

Deprecated/Consolidated:
- Separate per-feed article list screen replaced by `main?feedId=...`.

## ViewModel Patterns (Articles)
- Maintain a single source of truth (MainViewModel) for article lists; apply in-memory session read retention (`sessionReadIds`).
- When adding new filters (e.g. unread-only toggle), extend MainViewModel rather than introducing parallel flows.

## Optional Long Argument Pattern
```kotlin
composable(
  "main?feedId={feedId}",
  arguments = listOf(navArgument("feedId") { type = NavType.LongType; defaultValue = -1L })
) { backStackEntry ->
  val idRaw = backStackEntry.arguments?.getLong("feedId") ?: -1L
  val feedId = if (idRaw == -1L) null else idRaw
  MainScreen(navController, feedId)
}
```

## Adding New Filters
1. Extend `MainUiState` with new filter flag.
2. Store full list internally; derive filtered list in a private function similar to `applyFilterAndEmit`.
3. Avoid re-collecting flows unnecessarily; reuse existing collection and re-filter locally.
