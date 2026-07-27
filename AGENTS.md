# Repository Guidelines

hReader is an Android RSS reader built with Kotlin, Jetpack Compose and Koin. It syncs with
one self-hosted backend at a time: FreshRSS over the Google Reader API, or Miniflux.

## Commands

```bash
./gradlew lint test assembleDebug
```

Run all three before committing. Builds are slow — do not cancel them.

## Architecture

These invariants are enforced by ArchUnit tests in `app/src/test/.../architecture/`:

- Domain models (`data/model/`) depend on nothing but `java.time`. No Moshi, Room, Retrofit or
  OkHttp, and no knowledge of any backend's wire format.
- Each backend (`data/remote/freshrss/`, `data/remote/miniflux/`) owns its DTOs and maps them to
  domain models. Backends never reference each other.
- The UI never touches `data/remote`, Room entities or DAOs. It talks to ViewModels and domain
  models only.
- Repositories map between Room entities (internal) and domain models (public).

`FeedBackend` is the single interface the rest of the app talks to; `DelegatingFeedBackend`
resolves the active implementation per call from `ServerConfig`.

## Backend behaviour

- Exactly one backend is active at a time. Switching wipes every article, feed, content and image
  downloaded from the previous one, then resyncs.
- `EntriesPage.cursor` is opaque: a Google Reader continuation token for FreshRSS, an offset for
  Miniflux.
- Full article text always comes from the server — `fetch-content` on Miniflux, the per-feed
  original-article selector on FreshRSS — falling back to the content synced with the feed.
- The AI model list is fetched from OpenRouter rather than hardcoded, and the selected model is
  validated against it on startup.

## Configuration

Server address, credentials and the optional OpenRouter key live in `PreferencesManager`, edited
in Settings and offered during first-launch setup. Never put secrets in `BuildConfig` or the
build script.

## Style

- No Javadoc, no inline comments, no feature docs. Use meaningful names instead.
- Prefer `val`, small functions with early returns, `private`/`internal` visibility by default.
- `java.time` for dates, coroutines for async work.
- Compose: small stateless composables, state hoisted to ViewModels.
- Commits: small, scoped, imperative mood.

## Making changes

- A new backend capability means extending `FeedBackend` and implementing it in both backends.
- Room schema changes need a version bump in `AppDatabase`. The builder uses
  `fallbackToDestructiveMigration`, so local data is dropped rather than migrated.
- Update `di/appModule.kt` or `di/networkModule.kt` when adding types.
- Do not add dependencies without discussion.
