
# hReader

[![CI](https://github.com/Hiosdra/hReader/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/Hiosdra/hReader/actions/workflows/ci.yml)

hReader is an Android RSS reader for people who run their own FreshRSS or
Miniflux server. It downloads articles to your device, makes them comfortable
to read without an internet connection, and synchronizes your reading state
with the server you choose.

> hReader is an RSS client, not an RSS service. You need your own FreshRSS or
> Miniflux instance to use it.

## Key features

- all-article and unread views, feed filtering, and search;
- add feeds using a feed or website URL, including sharing a page from your browser;
- import and export subscriptions as OPML;
- mark articles read or unread, with optional automatic marking while reading;
- a reader with adjustable text size, Bionic Reading, saved reading position, and image support;
- switch between feed content, the full article, and the original web page;
- automatic background sync with Wi-Fi, roaming, frequency, and quiet-hours controls;
- prepare articles, images, and original pages for offline reading;
- read articles aloud using the system voice or a downloaded neural voice;
- optional AI article summaries and verification signals;
- local management of cached data, downloaded pages, images, and models.

## Supported servers

- **Miniflux** — sign in with an API token;
- **FreshRSS** — sign in with a username and API password; the integration is marked experimental in the app.

hReader works with one server at a time. Switching to another server removes
the previous account's downloaded articles, feeds, and images from the device,
then starts a fresh sync. Nothing is deleted from the previous server.

## Requirements

- Android 10 or newer;
- a running FreshRSS or Miniflux instance;
- the server address and API credentials;
- an internet connection for initial setup and synchronization.

## Installation

When available, install hReader from its [Google Play listing](https://play.google.com/store/apps/details?id=com.hiosdra.hreader).
For pre-release builds, look for ready-to-install packages in [GitHub Releases](https://github.com/Hiosdra/hReader/releases).
If there is no current APK there, the project does not currently provide a
stable user release — files from individual CI runs are test artifacts.

After installing the app:

1. Choose **Miniflux** or **FreshRSS**.
2. Enter your instance address, for example `https://rss.example.com`.
3. Enter your API credentials:
   - FreshRSS: your username and API password from **Profile → API management**;
   - Miniflux: an API token from **Settings → API keys**.
4. Select **Test connection**, then start synchronization.

## Getting started

After the first sync, open **Subscriptions** and add feeds using a feed or
website URL. If a website exposes more than one feed, hReader lets you choose
the one to subscribe to. You can also import your subscriptions from an OPML
file.

From the main screen you can:

- switch between all articles and unread articles;
- browse articles from a single feed;
- search downloaded articles;
- refresh your data manually;
- open **Settings**, **Sync health**, or **Travel mode**.

## Reading articles

hReader shows which copy of the content is currently being displayed. Depending
on the server and available data, you may see:

- content carried by the RSS feed;
- the full article text downloaded from the server;
- a locally saved page for offline reading;
- the original web page loaded from the internet.

If the full article is unavailable, the app shows the content carried by the
feed and makes that fallback clear in the reader. You can open the original
article in Chrome, share its link, or optionally open it through a selected
third-party paywall-bypass service. Those services are not part of hReader.

## Offline reading and Travel mode

In **Settings → Offline reading** or **Travel mode**, choose what to download
before you leave home:

- unread articles only, or up to 200, 500, or 1,000 articles;
- article content and optionally images;
- original web pages as an additional download.

The app shows progress and estimates network and storage usage. Full pages are
saved only when you start the full-page download — preparing articles for
offline reading does not automatically save every original website.

A saved page is a static snapshot. Without an internet connection, JavaScript,
logins, cookies, paywalls, forms, live updates, embedded frames, and streaming
media may not work. Articles already stored on the device remain available, but
refreshing and changing subscriptions require a connection to the server.

## Read aloud

Select **Read aloud** in an article. The system voice is available, along with
optional neural voices downloaded to the device, including:

- Supertonic 3 — a multilingual voice, including Polish;
- Coqui M-AILABS — a Polish female voice;
- Kokoro — English and Chinese voices;
- KittenTTS — an English voice.

You can adjust reading speed, choose a voice for a specific language, and tune
advanced parameters. If a downloaded voice cannot start, hReader automatically
tries the system voice.

## AI features

AI is optional. hReader provides:

- short article summaries;
- **verification signals**, an AI analysis of sourcing, tone, and balance;
- automatic pre-generation of summaries for unread articles from selected feeds.

You can choose between:

- **OpenRouter** — add your own API key and select a model from the list fetched from OpenRouter;
- **on-device Gemma 4 E2B** — download the large model to your device so article text is processed locally and can work offline once installation is complete.

hReader asks for confirmation when you manually start a cloud analysis.
Automatic pre-generated summaries require you to enable the option separately
for the selected feed. AI analysis is not fact-checking: the model cannot
browse the internet or verify claims.

## Synchronization

Synchronization can run automatically in the background. You can restrict it to
Wi-Fi, allow or block syncing while roaming, set quiet hours, and choose a safe
or fast mode. **Sync health** shows when feeds were last checked and whether the
most recent synchronization completed successfully.

## Privacy

hReader does not create user accounts, operate its own backend, show ads, or
use behavioral trackers. The app connects directly to the RSS server you
configure.

- Server credentials and the OpenRouter key are stored in the app's private storage and excluded from backups and device transfer.
- Articles, reading state, and settings are stored locally so offline reading works.
- Article text is sent to OpenRouter when you use cloud AI features; manual analysis requires confirmation, while automatic summaries require that you enable them for a feed first.
- When you use local Gemma, article text is not sent to a cloud provider.
- Optional diagnostics may send technical crash reports to Sentry and can be disabled in Settings.
- Downloading pages, images, voice models, and AI models may connect to the servers that host those resources.

Read the full [privacy policy](PRIVACY_POLICY.md).

## Important limitations

- FreshRSS support is currently experimental.
- hReader does not replace a FreshRSS or Miniflux server and cannot work without a configured instance.
- Offline reading only covers data downloaded in advance.
- AI results are useful for getting oriented in an article, not proof that its claims are true.
- Paywall bypass uses third-party services and is subject to their privacy policies.

## Help and issue reports

Report problems and suggestions in [GitHub Issues](https://github.com/Hiosdra/hReader/issues).
Include your phone model, Android version, selected backend, and a description
of the problem. Do not include API passwords, tokens, or complete server
credentials.

The source code, change history, and current project information are available
in the [hReader repository](https://github.com/Hiosdra/hReader).

The hReader project code is licensed under [GNU AGPL-3.0-only](LICENSE). See
the [third-party notices](NOTICE.md), the [versioned notices inventory](docs/third-party-notices-inventory-2026-09-22.md),
the [bundled runtime licenses](app/src/main/assets/licenses/README.md), and the
[TTS model notice](app/src/main/assets/tts/NOTICE) for the current
attribution and release-gate status.

For contribution and copyright-assignment requirements, see
[CONTRIBUTING.md](CONTRIBUTING.md), the [assignment process](docs/contributor-assignment-process.md),
and the [assignment agreement](docs/contributor-assignment-agreement.md).

For maintainers preparing a Google Play release, see the
[Google Play release guide](docs/google-play-release.md).
