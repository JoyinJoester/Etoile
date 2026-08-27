<h1 align="center">Etoile</h1>

<div align="center">

[中文](README.md) | **English**

<img src="image/etoile_launcher.png" alt="Etoile App Icon" width="220" />

<p><strong>A GitHub-focused standalone Android client</strong></p>
<p>Inbox · Repositories &amp; code · Issues · Pull requests · Actions · Releases · Explore</p>

<p>
	Links:
	<a href="https://linux.do" title="Linux.do">
		<img src="https://www.google.com/s2/favicons?domain=linux.do&sz=64" alt="Linux.do" width="22" />
		Linux.do
	</a>
	·
	<a href="https://github.com/Monica-Pass/Monica-for-Android" title="Monica Pass">
		Monica Pass
	</a>
</p>

[![Release](https://img.shields.io/github/v/release/JoyinJoester/Etoile?style=flat-square)](https://github.com/JoyinJoester/Etoile/releases)
[![Downloads](https://img.shields.io/github/downloads/JoyinJoester/Etoile/total?style=flat-square)](https://github.com/JoyinJoester/Etoile/releases)
[![Last Commit](https://img.shields.io/github/last-commit/JoyinJoester/Etoile?style=flat-square)](https://github.com/JoyinJoester/Etoile/commits/main)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square)](LICENSE)
[![QQ Group](https://img.shields.io/badge/QQ-1087865010-12B7F5?style=flat-square&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/2vTdTkHV3u)

[![Afdian](https://img.shields.io/badge/Afdian-JoyinJoester-ea4aaa?style=flat-square)](https://afdian.com/a/JoyinJoester)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-joyinjoester-29ABE0?style=flat-square&logo=kofi&logoColor=white)](https://ko-fi.com/joyinjoester)

</div>

<br>

Etoile is an independently maintained third-party GitHub client for Android, built with Jetpack Compose
and Material 3 Expressive. It brings the day-to-day GitHub loop onto your phone: triage notifications,
read code, handle issues and pull requests, and watch Actions runs.

> **Status: public test build.** Interfaces and layouts are still changing. This is not a stable release
> and not an official GitHub client.
>
> This project is not affiliated with, authorized by, or sponsored by GitHub, Inc. / Microsoft.
> GitHub and related marks belong to their respective owners.

---

## Read this first

### Who Etoile is for
- People who need to act on issue and pull-request reviews and notifications from a phone, not only in a browser.
- Users who want to read repository code, Actions logs, and releases natively instead of being pushed into a WebView.
- People who need multiple accounts switched quickly, or who do not want full GitHub Mobile for light usage.

### What you get
- **Inbox**: paginated notification threads, per-item Done / Unsubscribe, unread state, and in-place failure hints.
- **Repositories & code**: directory browsing, branch/tag switching, README rendering, raw files, commits, releases;
  plus separate read-only pages for branch lists, collaborator permissions, and webhooks.
- **Issues**: detail view with a unified management sheet — title/body editing, labels, assignees, milestone,
  close/reopen, conversation locking, and reactions.
- **Pull requests**: Conversation and Diff, inline review comments, reviewer requests, labels/assignees/milestone,
  and a merge confirmation bound to the head SHA (MERGE / SQUASH / REBASE).
- **Actions**: workflow and run lists, logs, re-run/cancel, enable/disable, and manual dispatch.
- **Explore**: five search tabs — Repositories / Users / Code / Issues / Pull Requests — sharing debouncing,
  pagination, and error states.
- **Profiles**: public profile, followers/following, repositories and stars (categorized locally).
- **Deep links**: recognized `github.com` issue, PR, and Actions run/job links open native screens;
  unknown paths still go to the browser.

### Quick install

1. Download the APK matching your device ABI from [Releases](https://github.com/JoyinJoester/Etoile/releases).
2. Install on Android 8.0+.
3. Sign in with the GitHub device flow; credentials stay in encrypted on-device storage.

### Known limitations
- Still a public test build; APIs and UI may change at any time.
- Relies on the GitHub REST API and is subject to rate limits. Throttling and cache fallback are shown
  explicitly in the UI — stale data is never presented silently.
- High-risk or externally visible operations (editing branch protection, adding or removing collaborators,
  delivering or deleting webhooks) intentionally redirect to GitHub's official settings pages;
  the client does not reproduce the web permission model.
- For merges, permission changes, and any final outcome, **the GitHub server response is authoritative**.

---

## Data and security boundaries

- Application ID: `app.etoile`; data is isolated by the Android application sandbox.
- Access tokens come from the GitHub device flow and are kept in encrypted on-device storage. Nothing is
  uploaded to third-party servers.
- Cache follows ETag / 304 validation and is cleared on sign-out or account switch; 401/403/4xx responses
  never surface another account's stale data.
- Webhook URLs, secrets, and similar sensitive configuration are kept out of client models and screens.
- This repository contains no telemetry or advertising SDKs.

### Implementation
- UI: Jetpack Compose + Material 3 / Material 3 Expressive, adaptive phone and tablet layouts.
- Layering: `feature` → `domain` ← `data`, with `domain` kept pure Kotlin and shareable across platforms.
- Networking: OkHttp directly against the GitHub REST API, with unified pagination, authenticated requests,
  and structured caching for read-only GETs.
- State: Kotlin Coroutines + Flow; immutable `UiState` exposed through `StateFlow`.
- Secure storage: Keystore-backed local token store.

---

## Provenance

Etoile's code base derives from [Monica Android](https://github.com/Monica-Pass/Monica-for-Android), a
local-first password manager, and reuses its Material 3 design language, navigation, and security components.
The repository has since converged on the GitHub client itself: the Steam feature layer, the Monica vault
modules (Bitwarden, KeePass, autofill, attachments, passkeys), and the MDBX storage engine have been removed.
See [`SOURCE.md`](./SOURCE.md) for the extraction baseline.

---

## Sponsorship

If Etoile is useful to you, support for continued development is welcome.

<div align="center">
<img src="image/support_author.jpg" alt="Support Etoile" width="320"/>
<br/>
<sub>WeChat / Alipay QR</sub>
<br/><br/>
</div>

You can also support via [Afdian](https://afdian.com/a/JoyinJoester) or [Ko-fi](https://ko-fi.com/joyinjoester).

---

## Development

### Prerequisites
- Latest stable Android Studio.
- JDK 17+.
- `compileSdk 35`, `targetSdk 34`, `minSdk 26` (Android 8.0+).
- Build baseline: AGP `8.7.3`, Kotlin `2.0.21`, Compose BOM `2026.03.00` (see `gradle/libs.versions.toml`).

### Useful commands

JVM tests only:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Build packages:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Note: `debug` builds also enable `minifyEnabled` and set `debuggable false`, so debug them the way you
would a release build.

Release signing is supplied externally through `keystore.properties` or `ETOILE_RELEASE_*` environment
variables. Never commit signing files or credentials. When signing configuration is missing, the build
produces an explicitly unsigned release package instead of falling back to the debug certificate.

### Code layout (current)
- `takagi/ru/monica/github` — all GitHub client business, layered as `feature` / `domain` / `data` / `component` / `design` / `navigation`.
- `takagi/ru/monica/data` / `utils` / `ui` — app-level settings, preference storage, theming, and base activity.
- The Java/Kotlin package remains `takagi.ru.monica` (inherited from Monica) while `applicationId` is `app.etoile`.

### Repository guide
- [`README.md`](./README.md) — Chinese overview (main)
- [`docs/architecture/GITHUB_MODULES.md`](./docs/architecture/GITHUB_MODULES.md) — layering, pagination, and UI maintenance conventions
- [`docs/architecture/GITHUB_UI_LAYOUT.md`](./docs/architecture/GITHUB_UI_LAYOUT.md) — responsive layout conventions
- [`docs/configuration/GITHUB_OAUTH.md`](./docs/configuration/GITHUB_OAUTH.md) — OAuth client ID configuration
- [`docs/release-signing.md`](./docs/release-signing.md) — external signing contract
- [`SOURCE.md`](./SOURCE.md) — extraction baseline from Monica Android
- [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md) — third-party notices

---

## Feedback and support

- Issues: [Etoile Issues](https://github.com/JoyinJoester/Etoile/issues)
- QQ group: `1087865010`
- Sponsor: [Afdian](https://afdian.com/a/JoyinJoester) · [Ko-fi](https://ko-fi.com/joyinjoester)

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=JoyinJoester/Etoile&type=Date)](https://star-history.com/#JoyinJoester/Etoile&Date)

---

## License

Copyright (c) 2025–2026 JoyinJoester

Etoile is released under the [GNU General Public License v3.0](LICENSE).

Additional third-party copyright and license information is in [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md).

GitHub and related trademarks belong to GitHub, Inc. / Microsoft and their respective owners.
This project is an unofficial third-party client.
