# Google Play release guide

This guide describes the repository-side release flow for hReader. It prepares
the app bundle and documents the Play Console work, but it does not publish an
app automatically.

## Current repository readiness

The project currently has the building blocks required for a Play upload:

- application ID: `com.hiosdra.hreader`;
- `minSdk 29` and `targetSdk 37`;
- a minified and resource-shrunk `release` build;
- release signing supplied through environment variables or an ignored local keystore;
- release HTTPS-only network policy;
- a public privacy policy linked from the app;
- CI artifacts for both a signed `hreader-release.aab` and a sideloadable `hreader-release.apk`;
- a SHA-256 checksum for each release artifact.

Google Play uses the Android App Bundle as the publishing format. The CI
release job builds the AAB when a commit reaches `master`; download it from the
workflow run rather than uploading the debug APK.

References:

- [Android App Bundles](https://developer.android.com/guide/app-bundle);
- [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk);
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756).

## One-time Play Console setup

1. Create the app in Play Console with the package name `com.hiosdra.hreader`.
2. Enroll the app in Play App Signing.
3. Use the repository's release keystore as the **upload key** only after confirming its certificate is registered in Play Console. Google should hold the app signing key.
4. Keep the keystore and all passwords outside Git. The CI secrets used by the release job are:
   - `RELEASE_KEYSTORE_BASE64`;
   - `RELEASE_KEYSTORE_PASSWORD`;
   - `RELEASE_KEY_ALIAS`;
   - `RELEASE_KEY_PASSWORD`.
5. Complete the main store listing, app content declarations, Data safety form, content rating, target audience, and testing details.
6. Add the public privacy-policy URL:
   `https://github.com/Hiosdra/hReader/blob/master/PRIVACY_POLICY.md`.

Do not replace or rotate the upload key after the first published release
without following Play Console's key-reset or key-upgrade process. Every new
upload must also increase `versionCode` in `app/build.gradle.kts`.

## Store listing draft

The default listing language should be English (United States). The current
Play Console limits are 30 characters for the app name, 80 characters for the
short description, and 4,000 characters for the full description.

Suggested values:

**App name**

```text
hReader
```

**Short description**

```text
Read your FreshRSS or Miniflux feeds offline
```

**Full description**

```text
hReader is an Android RSS reader for your own FreshRSS or Miniflux server.

Read all or unread articles, search your downloaded feed, manage subscriptions,
and keep your reading state synchronized with your server. The reader can show
feed content, full article text, saved offline pages, or the original web page.

Prepare articles, images, and pages before a trip. Travel mode lets you choose
unread articles only or a larger local reading list. Saved web pages are static
snapshots, so interactive features such as logins, forms, live updates, and
streaming media may require an internet connection.

Read articles aloud with the Android system voice or optional downloaded neural
voices. Optional AI features provide short summaries and verification signals.
Use OpenRouter with your own API key, or download the on-device Gemma model so
article text can be processed locally.

hReader does not provide its own RSS server or user account. You connect it to
the FreshRSS or Miniflux instance you choose. Credentials are stored in the
app's private storage and excluded from backup and device transfer.
```

Prepare screenshots that show the onboarding screen, article list, reader,
offline/travel controls, and settings. Use test content and never include real
server URLs, credentials, tokens, or private articles in screenshots.

## App access for Google review

hReader requires a user-provided FreshRSS or Miniflux server, so Play review
needs a stable, English-language test instance and credentials that work from
any review location. Provide the following in Play Console's **App access**
section:

- the server type and URL;
- a reusable test username and password/API token;
- the exact onboarding steps;
- an account with enough feeds and articles to exercise reading, syncing, and
  subscription management;
- any steps required to test OpenRouter, local AI, TTS, offline preparation, or
  paywall-bypass actions.

Never put these credentials in the repository, README, issue tracker, or CI
logs. Keep the test account active for the entire review period.

## Play Console declarations

### Privacy and Data safety

Use [PRIVACY_POLICY.md](../PRIVACY_POLICY.md) as the source of truth, then
complete the Data safety form for the release actually uploaded. The app does
not create hReader accounts, does not show ads, and does not use behavioral
analytics. The form still needs to describe local storage, the configured RSS
server, optional OpenRouter, optional Sentry diagnostics, article/model
downloads, and third-party paywall services.

Complete the data-deletion questions even though hReader does not create its
own user accounts. Data stored on the user's FreshRSS or Miniflux server is
controlled by that server's operator; hReader's local data can be removed from
Settings, by clearing app data, or by uninstalling the app.

### Foreground services

The manifest declares two foreground-service types and Play Console requires a
declaration for apps targeting Android 14 or newer:

- `dataSync` — user-visible synchronization, offline preparation, and voice/AI
  model downloads, each with a progress notification and cancel action;
- `mediaPlayback` — continued article read-aloud playback with media controls.

For each type, provide the user flow, explain why deferring or interrupting the
work harms the feature, and attach a short video showing how the user starts it.
The app only promotes sync workers to foreground when the operation is
user-visible; periodic background synchronization remains ordinary WorkManager
work. See Google's [foreground service requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en).

### Other declarations

- Ads: none.
- Account creation: none; the app connects to an existing user-selected RSS server.
- Notifications: used for synchronization, downloads, and read-aloud playback;
  the notification permission is requested at runtime where required.
- Content rating: complete the questionnaire based on the actual release.
- Target audience: select the audience that matches the intended general RSS-reader use case.

## Release procedure

1. Update `versionCode` and `versionName` intentionally in `app/build.gradle.kts`.
2. Run the repository validation with JDK 21:
   `./gradlew lint test assembleDebug bundleRelease`.
3. Merge the verified change to `master`.
4. Wait for the `release` job to finish and download `hreader-release.aab` plus `hreader-release.aab.sha256`.
5. Verify the checksum and upload the AAB to the **Internal testing** track first.
6. Install the Play-delivered build and test a fresh install, an update, sign-in to both supported backends, sync, notifications, TTS, offline reading, and local cleanup.
7. Promote the tested bundle through closed/open testing and then production when the Play Console review requirements are complete.

Publishing directly from CI is intentionally not configured. Adding a Play
service-account credential or an automated production track would expand the
release authority and should be a separate, explicit change.

## Final pre-submission checklist

- [ ] The Play app package is `com.hiosdra.hreader`.
- [ ] Play App Signing is enabled and the upload certificate matches CI.
- [ ] `versionCode` is higher than every previously uploaded bundle.
- [ ] The AAB checksum was verified before upload.
- [ ] Store description, icon, screenshots, and feature graphic are ready.
- [ ] Privacy policy URL is public and matches the submitted Data safety form.
- [ ] App access instructions and a reusable English test account are ready.
- [ ] `dataSync` and `mediaPlayback` foreground-service declarations and videos are ready.
- [ ] Internal testing passed on real Android devices.
- [ ] No credentials, signing keys, or private article content appear in artifacts or screenshots.
