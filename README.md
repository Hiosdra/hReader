# hReader - RSS/Feed Reader

A modern Android RSS/Feed reader built with Kotlin, Jetpack Compose, and MVVM architecture. It connects to a Miniflux backend API to synchronize feeds and articles.

## Architecture Overview

This project follows **MVVM with Clean Architecture** patterns:

### Data Layer (`data/`)
- **`local/`**: Room database, DAOs, entities, and repositories
- **`remote/`**: Retrofit API services, DTOs, and network repositories
- **`model/`**: Domain models shared across the application
- **`ai/`**: AI-related services for content processing
- **`paywall/`**: Services for paywall bypass functionality
- **`preferences/`**: SharedPreferences management

### UI Layer (`ui/`)
- **`main/`**: Main article list screen with feed filtering
- **`article/`**: Article reading screens and ViewModels
- **`feeds/`**: Feed management screens
- **`settings/`**: Application settings
- **`components/`**: Reusable UI components
- **`theme/`**: Material3 theming

### Infrastructure
- **`application/`**: Application class and lifecycle management
- **`navigation/`**: Navigation setup and routing
- **`di/`**: Dependency injection with Koin
- **`worker/`**: Background sync workers and WorkManager setup
- **`util/`**: Utility classes and helpers

## Key Features

### Navigation Patterns
- Consolidated navigation using parameterized routes
- `main?feedId={feedId}` pattern for filtered views instead of separate screens
- Clean separation between feature screens

### Data Synchronization
- Background sync with WorkManager
- Article content prefetching
- Image caching and management
- Offline-first architecture with Room database

### UI Architecture
- Jetpack Compose Material3 design
- MVVM pattern with ViewModels for each feature
- State-hoisted components
- Edge-to-edge design

## Recent Refactoring Improvements

This codebase was recently refactored for better organization and maintainability:

1. **File Organization**:
   - Moved `MyApplication` to dedicated `application/` package
   - Moved `WorkManagerSetup` to `worker/` package alongside related workers
   - Consolidated redundant screens and ViewModels

2. **Architecture Improvements**:
   - Removed redundant `FeedArticleListScreen` in favor of parameterized main screen
   - Consolidated article list functionality into unified MainScreen with filtering
   - Clean dependency injection setup with proper separation of concerns

3. **Navigation Simplification**:
   - Uses modern Navigation Compose with type-safe arguments
   - Parameterized routes for filtering (`main?feedId=...`)
   - Chrome Custom Tabs integration for external links

## Build & Test

```bash
# Run unit tests
./gradlew test

# Run lint checks
./gradlew lint

# Build debug APK
./gradlew assembleDebug

# Full clean build
./gradlew clean build
```

## Development

See [AGENTS.md](AGENTS.md) for detailed development guidelines, coding standards, and architectural patterns.

---

## TODO

### High Priority
1. Cache original article content images

### Medium Priority
1. AI article score for bullshit

### Low Priority
1. Detect if youtube miniplayer is open and shorten list of articles
