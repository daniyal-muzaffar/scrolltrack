# Architecture

One page on how ScrollTrack is put together.

## Components

| Component | What it does |
| --- | --- |
| `ReelAccessibilityService` | Watches Instagram (and only Instagram). Detects the Reels surface and forwards scroll events with page indexes to the tracker. |
| `TrackingService` | Foreground service. Owns the counting state machine, session lifecycle, and notifications. |
| `ReelCounter` / `ReelsDetection` | Pure counting logic (page-index primary, burst fallback) and Reels-surface detection from the view structure. |
| `DayAttribution` | Pure midnight-split and idle-cutoff math for crediting time to days. |
| `DbHelper` / `StatsRepository` | SQLite storage (`sessions`, `day_stats`) and the queries the UI reads. |
| `Prefs` | `SharedPreferences`: toggles, targets, themes, updater state. |
| UI (`MainActivity` + screens) | Five tab screens built programmatically through `UiKit` + `Theme` tokens. No layout XML. |
| `AppUpdater` / `UpdateCheck` | Update check, download, and install hand-off. |

## How a swipe becomes a stored count

```
Instagram window scrolls
  -> ReelAccessibilityService (TYPE_VIEW_SCROLLED, Instagram package only)
  -> ReelsDetection: is this the Reels surface? (pager id / selected Reels tab)
  -> TrackingService.onScroll(fromIndex, toIndex, surface)
  -> ReelCounter: page index increased -> +1 (300 ms debounce);
     surface unknown -> burst-cluster fallback with net-direction check
  -> StatsRepository: today's day_stats row += 1 (swipeCount)
```

The count is "swipes detected / Reels advanced", never "Reels verified watched".

## Sessions and daily stats

- A **session** starts when Instagram comes to the foreground with the service running and ends on screen-off, lock, app switch, or a 15-second heartbeat gap. Each session stores start/end, total duration, measured Reels time, and swipe count.
- **Day stats** are per local calendar day. Durations split at midnight, so a session crossing midnight credits each day its share. Counts are credited to the day the swipe happened.
- Orphaned sessions (app killed mid-session) are closed at their last-seen time on the next start, and their duration is credited — never inflated to "now".
- Schema is version 2. Upgrades run ALTER TABLE only; history is never dropped.

## How the updater works

```
App launch (at most once a day, auto-check on by default)
  -> GET https://raw.githubusercontent.com/daniyal-muzaffar/scrolltrack-updates/main/version.json
  -> UpdateCheck.parseVersionJson: versionCode, versionName, apkUrl, notes
  -> versionCode > installed ? prompt "Update available" (Update / Later)
  -> Update: download APK to the app cache with a progress dialog,
     verify it starts with the APK ("PK") magic, then hand it to the
     system installer via a content URI (CsvProvider). Android always asks.
```

Later records the version so it is not offered again; a manual check in Settings still shows it. The update is signed with the same key as the installed build, so data survives.

## Key design decisions

- **Data honesty over cleverness.** Missing data is shown as missing; projections are gated behind at least 7 days of real data; estimates are labelled as estimates.
- **Narrow accessibility scope.** The service is restricted to `com.instagram.android` and reads only structural signals (view IDs, scroll indexes). It never reads text, messages, or usernames.
- **No network except updates.** The only network call the app makes is the version-file check. No account, no analytics, no ads.
- **Pure logic is testable.** Counting, surface detection, day attribution, and update parsing are plain Java with no Android imports, covered by JVM unit tests (`tests/run_tests.sh`).
- **No Gradle.** The build is a manual `aapt2 → javac → d8 → zipalign → apksigner` pipeline in `build.sh`, because the Gradle daemon cannot run in the sandbox this project was built in. The pipeline is simple enough to read in one sitting.
- **Programmatic UI.** All screens are built in code through `UiKit` + `Theme`, which keeps the design system in one place and makes the Apple-style redesign a token change rather than a layout rewrite.
