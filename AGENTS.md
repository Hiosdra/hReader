# Repository Guidelines

This guide helps contributors and AI assistants make safe, minimal, and behavior-preserving changes to hReader.

## Quick Reference

**Tech Stack:** Kotlin 2.2.10 • Android Gradle 8.12.1 • Compose BOM 2025.08.00 • Room 2.7.2 • Koin 4.1.0 • Retrofit 3.0.0  
**Build Times:** Clean build 9+ min • Cached 3+ min • Test/Lint/Assemble 20-60s each  
**Architecture:** MVVM with Jetpack Compose • Miniflux API backend • Room local storage

## Project Structure & Module Organization

```
app/src/main/java/com/hiosdra/hreader/
├── MainActivity.kt              # App entry point
├── application/                 # Application setup (MyApplication, WorkManagerSetup)
├── data/
│   ├── ai/                      # AI integration (OpenRouter)
│   ├── local/                   # Room database
│   │   ├── dao/                 # Database access objects
│   │   ├── entity/              # Room entities (internal to data layer)
│   │   └── repository/          # Local data repositories
│   ├── model/                   # Domain models (UI-safe data classes)
│   ├── paywall/                 # Paywall/monetization
│   ├── preferences/             # SharedPreferences wrapper
│   ├── remote/                  # Retrofit API
│   │   └── dto/                 # Data transfer objects
│   └── repository/              # Data repositories (expose domain models)
├── di/                          # Dependency injection
│   ├── appModule.kt             # Main DI module
│   └── networkModule.kt         # Network DI module
├── navigation/                  # Navigation logic
├── ui/
│   ├── article/                 # Article reader screens
│   ├── components/              # Reusable UI components
│   ├── feeds/                   # Feed management
│   ├── main/                    # Main article list (unified, filterable)
│   ├── settings/                # Settings screens
│   └── theme/                   # Material3 theming
├── util/                        # Utility functions
└── worker/                      # Background sync (WorkManager)
```

**Tests:** `app/src/test/` (unit tests including ArchUnit architecture rules)  
**Resources:** `app/src/main/res/`  
**Android Config:** Kotlin/JVM 17, compileSdk=36, minSdk=29

## Architecture Patterns & Rules

### Layer Separation (Enforced by ArchUnit)
- **UI Layer** (`ui/`) → may depend on ViewModels, domain models, and util
- **UI Layer** → **MUST NOT** depend on Room entities (`data/local/entity/`)
- **Data Layer** → exposes domain models from `data/model/` to UI
- **Repositories** → map between entities (internal) and domain models (public API)
- **Navigation** → routes defined in `navigation/` package

### MVVM Flow
```
Composable Screen → ViewModel → Repository → (DAO | API Service)
                        ↓
                  Domain Models
                  (data/model/)
```

### Key Architectural Decisions
1. **Single Source of Truth:** MainViewModel handles unified article list with filters
2. **Session State:** Use in-memory session tracking (e.g., `sessionReadIds`) for temporary UI state
3. **Optional Navigation Args:** Use sentinel defaults (e.g., `-1L`) instead of nullable Long types
4. **Repository Pattern:** FeedRepository and similar wrap DAOs and provide domain-level operations

## Build, Test, and Development Commands

### Essential Commands
```bash
# Clean build (NEVER CANCEL - takes 9+ minutes)
./gradlew clean build --no-daemon  # Timeout: 60+ minutes

# Run unit tests (20s cached)
./gradlew test --no-daemon  # Timeout: 5+ minutes

# Run lint checks (17s cached)
./gradlew lint --no-daemon  # Timeout: 5+ minutes

# Build debug APK (17s cached, ~37MB output)
./gradlew assembleDebug --no-daemon  # Timeout: 5+ minutes

# Build release APK (~28MB output)
./gradlew assembleRelease --no-daemon  # Timeout: 5+ minutes
```

### Pre-Commit Checklist
Always run before committing:
```bash
./gradlew lint --no-daemon
./gradlew test --no-daemon
./gradlew assembleDebug --no-daemon
```

### Build Performance Expectations
- **First/clean build:** 9+ minutes (downloads dependencies)
- **Cached builds:** 3+ minutes
- **Individual tasks:** 20-60 seconds when dependencies cached
- **APK outputs:**
  - Debug: `app/build/outputs/apk/debug/app-debug.apk` (~37MB)
  - Release: `app/build/outputs/apk/release/app-release-unsigned.apk` (~28MB)

## Coding Style & Best Practices

### Kotlin Conventions
- Prefer `val` over `var` for immutability
- Use meaningful names instead of comments/documentation
- **NO JAVADOCS, NO INLINE COMMENTS, NO FEATURE DOCS**
- Small focused functions with early returns
- Default to `private`/`internal` visibility
- Use Kotlin idioms (extension functions, smart casts, etc.)
- Use `java.time` API for date/time operations
- Prefer Kotlin Coroutines for async operations

### Compose (Material3)
- Keep composables small and stateless
- Hoist state to ViewModels
- Use `remember`/`rememberSaveable` appropriately
- Follow `ui/theme/Theme.kt` for colors/typography
- Component naming: `<Feature><Type>` (e.g., `ArticleCard`, `FeedListItem`)

### Naming Conventions
- Classes/objects: `PascalCase`
- Functions/variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Files: match top-level type name
- ViewModels: `<Feature>ViewModel` (e.g., `MainViewModel`, `ArticleViewModel`)
- Repositories: `<Entity>Repository` (e.g., `FeedRepository`, `ArticleRepository`)

### Data Layer Patterns
- **Database changes:** Add migrations in Room setup (no destructive fallbacks)
- **API changes:** Update `MinifluxApiService`, wrap in repositories
- **Domain models:** Define in `data/model/`, map from entities in repositories
- **DTOs:** Use Moshi annotations for JSON serialization

## Navigation & Routing

Current routes:
```kotlin
"main"                                    // All articles (unified list)
"main?feedId={feedId}"                   // Filtered by feed (Long, default=-1L)
"feeds"                                  // Subscriptions list
"feed/{feedId}"                          // Feed details
"add_feed"                               // Add new feed
"article/{articleIds}/{initialIndex}"    // Paged article reader
"settings"                               // Settings
```

### Navigation Guidelines
- **NO nullable Long arguments** - use sentinel defaults (e.g., `-1L`)
- Optional filters → query params with defaults, not new routes
- Keep filtering logic in ViewModels (e.g., `MainViewModel.setFeed()`)
- Prefer extending existing screens over creating duplicates

## Testing Guidelines

### Architecture Tests (ArchUnit)
- Enforce UI layer cannot depend on Room entities
- Verify package dependencies follow architecture rules
- Tests located in `app/src/test/`

### Unit Testing Patterns
- Test at repository/ViewModel boundaries
- Keep tests deterministic (no real network, use fakes/mocks)
- Naming: `<TargetClass>Test` (e.g., `FeedRepositoryTest`)
- Test method names describe behavior (e.g., `fetch_returnsCachedOnError`)
- Run with `./gradlew test`
- Add tests only where clear patterns exist

### Current Test Count
- 9+ unit tests including architectural validation

## Dependency Management

### Key Dependencies (Verified Current Versions)
```gradle
// Core
kotlin = "2.2.10"
androidGradlePlugin = "8.12.1"

// Compose (BOM-managed, don't hardcode versions)
composeBom = "2025.08.00"
activityCompose = "1.10.1"
navigationCompose = "2.9.3"

// Architecture
lifecycleRuntimeKtx = "2.9.2"
coroutinesAndroid = "1.10.2"
koinBom = "4.1.0"

// Persistence
room = "2.7.2"
workManager = "2.10.3"

// Networking
retrofit = "3.0.0"
retrofitMoshi = "3.0.0"
okhttp = "5.1.0"
moshi = "1.15.2"

// UI
coilCompose = "2.7.0"
accompanistPager = "0.36.0"  // DEPRECATED - migrate to androidx pager

// Testing
junit = "4.13.2"
archunit = "1.4.1"
```

### Dependency Guidelines
- **DO NOT** add new dependencies without discussion
- **DO NOT** hardcode Compose artifact versions (use BOM)
- **VERIFY** Retrofit 3.x usage is intentional
- **PLAN** migration from Accompanist Pager to Foundation Pager

## Security & Configuration

### Security Best Practices
- **NEVER** hardcode credentials, API keys, or tokens
- Use `BuildConfig` values for sensitive configuration
- Avoid logging sensitive request/response bodies
- Don't measure/log execution times for operations
- Handle sensitive data appropriately in preferences

### Existing Stack (DO NOT CHANGE)
- Retrofit + Moshi + OkHttp (with AuthInterceptor)
- Koin for dependency injection
- Room database (migrations required, no destructive fallbacks)
- WorkManager for background sync

## Commit & Pull Request Guidelines

### Commit Standards
- Small, scoped, focused changes
- Imperative mood (e.g., "Fix sync retry backoff", "Add search to MainScreen")
- Avoid unrelated changes in same commit
- Reference issues when applicable

### Pull Request Requirements
- Summary of changes and rationale
- Link to related issues
- Screenshots/GIFs for UI changes
- Call out DI module changes explicitly
- Document database migrations
- Verify lint, tests, and build pass

### Code Review Checklist
- [ ] Follows MVVM architecture layers
- [ ] UI doesn't depend on Room entities
- [ ] Domain models used between layers
- [ ] DI modules updated if new types added
- [ ] Database migrations if schema changed
- [ ] Tests added/updated where applicable
- [ ] Lint passes without new warnings
- [ ] Build succeeds

## Agent-Specific Instructions

### Core Principles
1. **Minimal Changes:** Make surgical, precise edits - change as few lines as possible
2. **Preserve Behavior:** Don't modify working code unless explicitly requested
3. **Respect Architecture:** Follow MVVM layers strictly (UI → ViewModel → Repository → DAO/API)
4. **Layer Isolation:** UI uses domain models, never Room entities
5. **Existing Patterns:** Follow established patterns in the codebase

### Common Tasks

#### Adding New Features
1. Update entities in `data/local/entity/` (if needed)
2. Update DAOs in `data/local/dao/`
3. Create/update domain models in `data/model/`
4. Create/update repositories in `data/repository/` or `data/local/repository/`
5. Add ViewModels in appropriate `ui/` subdirectory
6. Create Compose screens in matching `ui/` subdirectory
7. Update DI modules in `di/appModule.kt` or `di/networkModule.kt`
8. Run lint and tests before committing

#### Working with Database
- Entities are internal to data layer
- Repositories expose domain models
- Add migrations in `appModule.kt` Room setup
- Never use `fallbackToDestructiveMigration()`

#### Working with API
- Define services in `data/remote/`
- Use DTOs with Moshi for serialization
- Wrap API calls in repositories
- Handle errors appropriately

#### Adding Filters/Features to Main Screen
- Extend `MainUiState` with new filter properties
- Update `MainViewModel` filtering logic
- Derive filtered lists from full list internally
- Avoid re-collecting flows unnecessarily

### Build Validation
```bash
# Always validate before requesting review
./gradlew lint test assembleDebug --no-daemon
```

### Performance Notes
- **NEVER CANCEL** Gradle builds - they take time but must complete
- Set appropriate timeouts (60+ min for clean builds)
- First builds download dependencies and take longer
- Subsequent builds use Gradle cache and are faster

## Troubleshooting

### Common Issues
- **Build fails:** Run `./gradlew clean` then rebuild
- **Dependency issues:** Check versions in `build.gradle.kts`
- **Room warnings:** 'annotationProcessor' vs 'kapt' warnings are expected (using KSP)
- **Timeout errors:** Increase timeout, don't cancel builds
- **Architecture violations:** Check ArchUnit test failures for details

### Getting Help
- Review `.github/copilot-instructions.md` for comprehensive guidance
- Check recent commits for patterns: `git log --oneline -20`
- Examine existing implementations before adding new features
- Follow established patterns in similar components

---

**Last Updated:** Based on project state as of September 2024 with Kotlin 2.2.10, Compose BOM 2025.08.00

