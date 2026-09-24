# hReader Visual Design System

Draft 1.3 · dark theme only

## Principles

- A calm editorial look: warm graphite, a sand accent, and muted status colors.
- Article content takes priority over app chrome; each screen has one prominent action.
- Colors have semantic roles. Do not communicate state through color alone.
- Keep the article list flat. Use cards to group settings or related states.
- Use shared Material 3 tokens for spacing, typography, and shapes.

## Colors

Values match `DarkColorScheme` in `presentation/theme/Color.kt`.

| Role | Color |
| --- | --- |
| Background | `#181611` |
| Low surface | `#221F19` |
| Group surface | `#2C2722` |
| Raised surface | `#35312A` |
| Primary text | `#E7E4DE` |
| Secondary text | `#CBC5BB` |
| Primary accent | `#E0C295` |
| Secondary accent | `#D4C2AE` |
| Success / warning / error | `#A4D388` / `#E7C358` / `#FFB3A3` |
| Subtle outline | `#4B4640` |

Use the matching `on*` role for text on tinted surfaces. Pair success, warning, and error colors with text or an icon; reserve `error` for actual errors.

## Typography

Use the system font and Material 3 scale. Use `Bold` sparingly; titles use `Medium` or `SemiBold`.

| Role | Style |
| --- | --- |
| Article title | `headlineSmall`, 24/32 sp, SemiBold |
| View title | `titleLarge`, 22/28 sp, SemiBold |
| Row or group title | `titleMedium`, 16/24 sp, Medium |
| Small heading | `titleSmall`, 14/20 sp, SemiBold |
| Article body | `bodyLarge`, 16/24 sp |
| Preview and description | `bodyMedium`, 14/20 sp |
| Supporting text | `bodySmall`, 13/18 sp |
| Metadata / secondary label | `labelMedium`, 12/16 sp / `labelSmall`, 11/16 sp |

Article reading preferences remain under the reader's control.

## Spacing and shapes

`HReaderSpacing` provides a 4 dp grid: `space1`–`space6` and `space8` map to 4, 8, 12, 16, 20, 24, and 32 dp. Use 16 dp screen margins and 24 dp between independent sections.

Material 3 corner sizes are 4, 8, 12, 16, and 28 dp. Use full rounding for filters and short status chips. Do not use shadows as default separators or nest cards.

## Components

- **List:** Keep unread/all filters in one selection group. Retain date headings and subtle dividers.
- **Article row:** Show source and time, a title up to two lines, and a preview up to two lines. Use an 80 dp `Crop` thumbnail.
- **Read state:** Use SemiBold and an accent marker for unread titles; keep read titles legible. Give the read-state control a separate 48 dp touch target.
- **Reader:** Keep the title, source, and metadata in a compact header. Put original-page, share, and external-tool actions in the app-bar menu.
- **AI:** Keep summaries collapsed until a result is available. Explain cloud processing and request confirmation before sending article text.
- **Images:** If the lead-image URL already occurs in the article HTML, omit the duplicate header image. App styling must not override source HTML.
- **Settings:** Group related settings in cards. A collapsed group shows a title and a summary of up to two lines; controls share the same surface.
- **States and actions:** Distinguish empty results, missing subscriptions, and errors, and provide the relevant next action. Use filled, tonal, and text buttons for primary, secondary, and tertiary actions. Confirm destructive actions when their effects are hard to reverse.

## Accessibility and motion

- Touch targets are at least 48 × 48 dp. State is not communicated through color alone.
- Primary text has at least 4.5:1 contrast. Action names and status text remain readable with system text scaling.
- The list and reader were checked on API 37 at 130% system font scale.
- `MotionDuration`: 120 ms for exit, 140 ms for quick changes, and 180 ms for standard changes. Respect the system's reduced-animation setting.

## Product, UX, and engineering review

- **Product:** Make topic, source, and read state easy to scan; keep filtering, opening an article, and changing its read state distinct.
- **UX:** Short previews and flat rows improve scanning; a compact reader header brings the article into view sooner.
- **Engineering:** Reuse Material 3 and Paging 3 without new dependencies. Keep models and sync unchanged, and keep server HTML separate from app chrome.

## Scope and validation

Draft 1.3 covers shared tokens and the main list and reader patterns. Next, apply them to settings, empty and error states, and offline flows. The populated article flow was checked on an API 37 emulator with the local [Miniflux mock](docs/local-testing.md).
