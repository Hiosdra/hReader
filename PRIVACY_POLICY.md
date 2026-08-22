# hReader Privacy Policy

Last updated: 22 August 2026

## Publisher and contact

hReader is published by Oskar Drozda.

Privacy contact: the [hReader GitHub repository](https://github.com/Hiosdra/hReader).

## Scope of this policy

hReader is a client application for a self-hosted RSS reader. The application does not provide
its own user accounts, does not operate a publisher-owned backend, and does not sell data. We do
not use advertising, behavioral analytics, or additional trackers.

This policy describes the data hReader accesses, stores locally, or transfers to provide features
selected by the user. Data processed by Google Play during installation, updates, or app operation
is processed by Google under the [Google Privacy Policy](https://policies.google.com/privacy).
hReader does not receive that data from Google.

## Data stored on the device

hReader may store locally:

- the address and type of the selected FreshRSS or Miniflux server;
- the FreshRSS username and API password, or the Miniflux API token;
- the optional OpenRouter API key;
- app settings, including the crash-reporting setting;
- synchronized feeds, titles, authors, dates, links, article content, images, and read/star state;
- locally generated article summaries and credibility-analysis results.

This data is stored in the app's private storage area. The file containing login credentials and the
OpenRouter key is excluded from backups and device transfer. Local data can be removed from the
app's settings, by clearing the app's data, or by uninstalling hReader.

## Connections to RSS servers

The user chooses the FreshRSS or Miniflux instance to which hReader connects. The application
sends that instance the data required by the selected protocol, including credentials or a token,
sync requests, subscription data, article metadata and content, and read/star state changes.

Documentation for the protocols used:

- [FreshRSS Google Reader API](https://freshrss.github.io/FreshRSS/en/developers/06_GoogleReader_API.html);
- [Miniflux API](https://miniflux.app/docs/api.html).

The operator of the configured FreshRSS or Miniflux instance may process and log this data, the
IP address, request time, and other information determined by the server configuration. Oskar
Drozda does not control that instance's retention or logs. Contact the server operator about data
stored by that server.

## OpenRouter

Article summaries and credibility analysis are optional. After the user saves their own OpenRouter
API key and starts one of these features, hReader sends directly to OpenRouter:

- the article title and text for a summary;
- the article text and limited metadata, such as the title, author, feed name, publisher domain,
  and publication date, for credibility analysis;
- the selected model identifier and technical request data.

OpenRouter may forward a request to the selected model provider under its terms and privacy policy.
See the [OpenRouter Privacy Policy](https://openrouter.ai/privacy). hReader does not receive the
OpenRouter API key outside the device and does not send article content to OpenRouter unless the
user starts an AI feature.

The available OpenRouter model list may be fetched when the AI settings are opened or manually
refreshed. This request does not contain article content, but OpenRouter servers may see standard
connection data such as the IP address and request time. If the user selects the local Gemma model,
the article title and content are processed on the device and are not sent to OpenRouter or an
external model provider. The Gemma model itself is downloaded from Hugging Face; that transfer is
the model file, not article content.

## Sentry crash reports and diagnostics

Sentry crash and diagnostic reporting is enabled by default when the app has a Sentry server
configured. The user can disable it at any time in the privacy and diagnostics settings. After it
is disabled, hReader does not send new reports.

When enabled, reporting may include technical information needed for diagnosis, such as the
exception, stack trace, app version, Android version, device model, and app component. hReader
disables default PII, screenshots, view hierarchy, sessions, NDK, automatic breadcrumbs, and
automatic session tracking. Sentry processes data under its [Privacy Policy](https://sentry.io/privacy/).

## Other network connections

hReader may directly fetch feeds, article pages, images, and resources needed to display content
from addresses in the configuration or supplied by articles. It may also download TTS models from
their sources when the user enables that feature, and the Gemma model from Hugging Face. These
source servers may see standard network-request data, such as the IP address and connection time,
under their own policies. hReader does not create its own user profile from this data.

## Services for opening articles through external sites

The optional paywall-bypass feature opens an article through a service selected by the user. When
used, hReader opens the selected service's address and passes it the original article address. The
available services are:

- Smry.ai (`smry.ai`);
- RemovePaywall.com (`www.removepaywall.com`);
- RemovePaywalls.com (`removepaywalls.com`);
- PaywallBuster (`paywallbuster.com`);
- Archive.ph (`archive.ph`);
- Wayback Machine (`web.archive.org`);
- Archive Buttons (`www.archivebuttons.com`);
- Bypass Paywall Reader (`www.bypasspaywallreader.com`).

These are independent external services, not a hReader publisher-owned backend. Their operators
may receive the article address, IP address, request time, and other standard browser data, and
apply their own privacy and retention policies.

Apart from the configured RSS server, optional OpenRouter and Sentry services, content and model
sources, and the third-party paywall services described above, hReader does not communicate with
any publisher-owned backend and does not collect additional data.

## Sharing data

We do not sell data or share it for advertising purposes. Data is transferred only:

1. to the FreshRSS or Miniflux server selected by the user;
2. to OpenRouter when the user uses an AI feature;
3. to Sentry when diagnostic reporting is enabled;
4. to source servers when needed to fetch requested content or a model;
5. to the selected external service when the user opens an article through the paywall-bypass
   feature.

## Retention and deletion

Local data remains on the device until the user removes it from the settings, clears the app's
data, or uninstalls hReader. Data stored by an RSS server, OpenRouter, Sentry, an external service,
or a source server is subject to that operator's policies. Disabling Sentry stops future sending,
but does not automatically delete events already delivered to Sentry.

For questions about local data or this policy, use the [hReader GitHub repository](https://github.com/Hiosdra/hReader).
Deleting data from an external server requires contacting that server's operator.

## Security

hReader uses private app storage and excludes secrets from backups and device transfer. Production
builds do not allow unencrypted HTTP; the HTTP exception is available only in the debug variant for
local test servers. The user is responsible for securing and configuring their RSS instance and
protecting their OpenRouter key.

## Changes to this policy

This policy may be updated when the app's behavior or legal requirements change. The current
version is published with the hReader source code.
