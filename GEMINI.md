# hReader - Android RSS/Feed Reader App

hReader is an Android RSS/Feed reader application built with modern Android development practices and technologies.

## Architecture & Technology Stack

### Core Architecture
- **MVVM (Model-View-ViewModel)** with Jetpack components
- **Repository Pattern** for data abstraction
- **Koin** for dependency injection
- **Kotlin Coroutines** for asynchronous programming

### UI & Presentation
- **Jetpack Compose** with Material 3 design system
- **Navigation Compose** for app navigation
- **State hoisting** and composition-local patterns
- **Dark/Light theme support** with dynamic theming

### Data & Storage
- **Room Database** for local storage and offline reading
- **Retrofit + Moshi** for network communication
- **Miniflux API** as the backend service
- **Coil** for image loading and caching

### Background & Sync
- **WorkManager** for background synchronization
- **Periodic sync** for feed updates
- **Offline-first** data architecture

## Key Components

### Data Layer (`app/src/main/java/com/hiosdra/hreader/data/`)
- **Remote**: API services, DTOs, and network handling
- **Local**: Room database, DAOs, entities, and repositories
- **Preferences**: SharedPreferences wrapper for settings
- **AI**: OpenRouter integration for AI features

### UI Layer (`app/src/main/java/com/hiosdra/hreader/ui/`)
- **Main**: Article list and main navigation
- **Article**: Article reading and content display
- **Feeds**: Feed management and subscription
- **Settings**: App configuration and preferences
- **Components**: Reusable UI components
- **Theme**: Material 3 theming and colors

### Infrastructure
- **DI** (`di/`): Koin dependency injection modules
- **Navigation** (`navigation/`): Navigation logic and routes
- **Worker** (`worker/`): Background sync workers
- **Util** (`util/`): Utility classes and extensions

## Development Guidelines

### Code Style
- Follow **Kotlin coding conventions**
- Use **val over var** where possible
- Prefer **private/internal visibility** by default
- Keep functions **small and focused**
- Use **early returns** to reduce nesting

### Jetpack Compose Best Practices
- Keep composables **small and stateless**
- **Hoist state** appropriately
- Use `remember` and `rememberSaveable` correctly
- Follow **Material 3** design principles
- Optimize for **recomposition performance**

### Data Handling
- Implement **proper error handling** for network requests
- Use **Flow** for reactive data streams  
- Handle **offline scenarios** gracefully
- Respect **Android lifecycle** in ViewModels and Repositories

### Testing
- Write **unit tests** for ViewModels and Repositories
- Test **architecture compliance** with ArchUnit
- Keep tests **deterministic** and avoid real network calls
- Use **fakes and mocks** for external dependencies

### Security & Performance
- Never hardcode **API keys or credentials**
- Use **BuildConfig** for configuration values
- Implement proper **input validation**
- Optimize for **battery usage** in background work
- Handle **memory efficiently** in image loading

## RSS/Feed Reader Specific Considerations

### Feed Management
- Support various **RSS/Atom feed formats**
- Handle **feed parsing errors** gracefully  
- Implement **feed discovery** from URLs
- Manage **feed categories and organization**

### Article Reading
- Support **offline reading** capabilities
- Handle **article content extraction**
- Implement **reading progress tracking**
- Support **different reading modes** (light/dark, font sizes)

### Synchronization
- Implement **efficient sync strategies**
- Handle **sync conflicts** appropriately
- Respect **network conditions** and data usage
- Provide **sync status feedback** to users

### User Experience
- Maintain **responsive UI** during sync operations
- Implement **proper loading states**
- Handle **network errors** with user-friendly messages
- Support **accessibility** features

## Common Patterns in hReader

### ViewModels
- Extend `ViewModel` with Koin injection
- Use `StateFlow` for UI state
- Handle **configuration changes** properly
- Implement **proper cleanup** in `onCleared()`

### Repositories
- Abstract **data sources** (local/remote)
- Implement **offline-first** strategies
- Use **suspend functions** for async operations
- Handle **exceptions** at repository level

### Composables
- Follow **single responsibility** principle
- Accept **lambda parameters** for user interactions
- Use **modifier parameters** for customization
- Implement **preview functions** for development

## Build & Development

### Commands
- `./gradlew build` - Full build with tests and lint
- `./gradlew test` - Run unit tests
- `./gradlew lint` - Run lint checks
- `./gradlew assembleDebug` - Build debug APK

### Dependencies
- Always check **compatibility** with existing versions
- Follow **semantic versioning** principles
- Update **proguard rules** if needed
- Test on **different API levels** (minSdk=29, targetSdk=36)

When contributing to hReader, please follow these guidelines and maintain consistency with the existing codebase architecture and patterns.