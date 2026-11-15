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
  -> GET https://raw.githubusercontent.com/daniyal-muzaffar/scrolltrack/main/version.json
  -> UpdateCheck.parseVersionJson: versionCode, versionName, apkUrl, notes
  -> versionCode > installed ? prompt "Update available" (Update / Later)
  -> Update: download APK to the app cache with a progress dialog,
     verify it starts with the APK ("PK") magic, then hand it to the
     system installer via a content URI (CsvProvider). Android always asks.
```

Later records the version so it is not offered again; a manual check in Settings still shows it. The update is signed with the same key as the installed build, so data survives.

## Anonymous usage stats (opt-in, v1.5.0)

Settings has a "Share anonymous usage stats" toggle, off by default. When on, the app calls the `submit_stats` RPC once a day with:

```json
{"p_install_id":"<random uuid>","p_day":"2026-11-15","p_swipes":623,"p_minutes":151,"p_app_version":"1.5.0"}
```

- The install ID is a UUID generated on the phone (`Prefs.getInstallId()`); it cannot be linked to a person or a device.
- The backend is a free Supabase project (`scrolltrack-stats`, Mumbai). The full schema lives in `supabase/schema.sql`. The `daily_stats` table has a primary key on (`install_id`, `day`) — that is the unique constraint — and RLS is on with **no table access for anon at all** (policies dropped, privileges revoked). Anon can only EXECUTE two SECURITY DEFINER functions: `submit_stats` (validates and upserts one row) and `stats_summary` (aggregates only, never individual rows).
- `submit_stats` rejects impossible values: empty install ID, negative counts, swipes over 100,000, minutes over 1,440, a future day, or a day more than 365 days in the past. It then inserts or updates that one row.
- The table endpoint is deliberately NOT used: PostgREST's `merge-duplicates` upsert was rejected by RLS even with permissive INSERT + UPDATE policies (cause unresolved), so all writes go through the function instead.
- Uploads happen whenever the app comes to the foreground (`MainActivity.onResume` → `StatsUploader.maybeUploadOnForeground`), and immediately when the toggle is switched on. The app re-uploads a day only when its totals changed (`AnonymousStats.shouldUpload`), at most a few requests a day.
- Failures are silent — stats never nag the user — and turning the toggle off stops uploads immediately.
- The pure core (`AnonymousStats`: payload, JSON, upload decision) is JVM-tested; the network call (`StatsUploader`) follows the same `HttpURLConnection` pattern as the updater.
- The endpoint and anon key live in `AnonymousStats` (`ENDPOINT`, `ANON_KEY`). The anon key is public by design — it grants nothing but EXECUTE on the two RPCs. Never put the service_role key in the app.

## Key design decisions

- **Data honesty over cleverness.** Missing data is shown as missing; projections are gated behind at least 7 days of real data; estimates are labelled as estimates.
- **Narrow accessibility scope.** The service is restricted to `com.instagram.android` and reads only structural signals (view IDs, scroll indexes). It never reads text, messages, or usernames.
- **Network is opt-in only beyond updates.** Besides the version-file check, the only network call is the opt-in anonymous stats upload (off by default). No account, no ads.
- **Pure logic is testable.** Counting, surface detection, day attribution, and update parsing are plain Java with no Android imports, covered by JVM unit tests (`tests/run_tests.sh`).
- **No Gradle.** The build is a manual `aapt2 → javac → d8 → zipalign → apksigner` pipeline in `build.sh`, because the Gradle daemon cannot run in the sandbox this project was built in. The pipeline is simple enough to read in one sitting.
- **Programmatic UI.** All screens are built in code through `UiKit` + `Theme`, which keeps the design system in one place and makes the Apple-style redesign a token change rather than a layout rewrite.
