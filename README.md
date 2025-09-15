# TODO

## High Prio
1. Cache original article content images

## Med Prio
1. AI article score for bullshit

## Low prio
1. Detect if youtube miniplayer is open and shorten list of articles

# HReader

HReader is an Android app built with Kotlin and Jetpack Compose. It follows a modular MVVM-based architecture with a clear separation of concerns to keep the codebase maintainable and scalable.

## Architecture Overview
- Presentation (UI): Jetpack Compose screens and ViewModels (MVVM).
- Domain: Simple business logic lives in ViewModels and Repositories (no separate domain module yet for minimalism).
- Data: Local (Room), Remote (Retrofit), Preferences, and AI integrations.
- DI: Koin for dependency injection (including WorkManager integration).
- Background work: WorkManager with Koin worker injection.

## Package Structure
- com.hiosdra.hreader
  - MyApplication: Application entry point and Koin initialization.
- com.hiosdra.hreader.navigation
  - AppNavigation, ChromeCustomTabs.
- com.hiosdra.hreader.ui
  - article: ArticleScreen, ArticleViewModel, list components.
  - feeds: FeedsScreen, FeedDetailScreen, FeedsViewModel; feeds/add: AddFeedScreen, AddFeedViewModel.
  - main: MainScreen, MainViewModel.
  - settings: SettingsScreen.
  - components: Shared UI components.
  - theme: Material 3 theme files.
- com.hiosdra.hreader.data
  - local: Room database, DAOs, entities, repositories.
  - remote: Retrofit service, DTOs, repository, AuthInterceptor.
  - preferences: PreferencesManager.
  - ai: AI models and services.
  - paywall: PaywallBypassService.
- com.hiosdra.hreader.di
  - appModule, networkModule: Koin modules and Worker bindings.
- com.hiosdra.hreader.config
  - WorkManager setup utilities.
- com.hiosdra.hreader.worker
  - Worker implementations (ContentSyncWorker, ArticleContentSyncWorker).
- com.hiosdra.hreader.util
  - Utilities (BionicReadingProcessor, ImageLoader, etc.).

## Recent Refactor (Aug 2025)
- Kept MyApplication in root package `com.hiosdra.hreader` for better discoverability; updated AndroidManifest accordingly.
- Moved WorkManager setup functions to `com.hiosdra.hreader.config` to distinguish them from Worker implementations.
- Removed redundant FeedArticleListScreen and ArticleListViewModel; unified article logic in ArticleViewModel and ArticleScreen.
- Cleaned up DI bindings for removed ViewModel.

These changes reduce duplication, align files with their responsibilities, and make navigation and DI wiring simpler.

## Building and Testing
- Build: ./gradlew assembleDebug
- Run unit tests: ./gradlew testDebugUnitTest

Unit tests cover data layer rules (ArchUnit), utilities, AI model serialization, repositories, and ViewModels.
