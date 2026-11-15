# Changelog

All notable changes to ScrollTrack are documented here, newest first.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.5.1] - 2026-11-15

### Fixed

- Anonymous stats now upload whenever the app comes to the foreground, not just on a fresh launch. Previously, returning to an already-open app never sent the latest totals, so the dashboard could stay stale until the app was force-closed and reopened. Uploads are still gated: only when opted in and only when today's totals changed.

## [1.5.0] - 2026-11-15

### Added

- Opt-in anonymous usage stats: a Settings toggle ("Share anonymous usage stats", off by default) sends one row a day — a random install ID, the date, and that day's swipe total and Instagram minutes — to a Supabase backend, so the developer can see how many people use the app and how much they scroll. No names, no session times, no content. Turning it off stops uploads immediately. Backend: free Supabase project `scrolltrack-stats` (Mumbai), table `daily_stats` with RLS (anon INSERT/upsert only) and an aggregate `stats_summary()` RPC for the dashboard.

## [1.4.2] - 2026-11-15

### Changed

- Repository renamed from `scrolltrack-updates` to `scrolltrack`. The in-app updater now checks the new repository URL; installed copies keep working through GitHub's redirect from the old name.

## [1.4.1] - 2026-11-14

### Changed

- Test release to verify the in-app updater end to end. Version bump only; no functional changes.

## [1.4.0] - 2026-11-14

### Added

- In-app updater: the app checks a version file on GitHub once a day and offers to download and install new versions from inside the app. Updates install over the old version, keeping all history and settings.
- Settings gains an "App updates" section with an automatic-check toggle (on by default) and a manual "Check for updates" row.

## [1.3.0] - 2026-11-14

### Changed

- Apple-inspired visual redesign: grouped backgrounds, 18dp cards, a floating translucent tab bar, native-style segmented controls, and lime as the single accent. Visual only; no logic, data, or features changed.

## [1.2.0] - 2026-11-14

### Fixed

- Counting now detects the Reels surface and counts one increment per completed advance to a new Reel (page-index based), with an upward-swipe fallback when the surface is unknown. Feed, Stories, Explore, comments, and DMs no longer count; bounces and refreshes count as zero.
- Sessions close on screen-off, lock, and app switch; durations split at local midnight; a 45-second idle cutoff stops counting idle time; crashed sessions are recovered at their last-seen time instead of being lost or inflated.
- Database migration is now ALTER TABLE only — upgrading no longer wipes history. The Highest Daily Count is preserved.

## [1.1.2] - 2026-10-22

### Added

- Daily Swipe Title on the Dashboard, driven by today's swipe count.

## [1.1.1] - 2026-10-20

### Added

- "Highest Daily Count" record card on the Dashboard (all-time record, earliest day wins on ties).

### Removed

- "Avg / session" and "Longest session" cards from the Dashboard.

## [1.1.0] - 2026-10-20

### Added

- Life Impact tab: annual impact (hours and full days per year, 365-dot year grid) and lifetime projections for 10/20/30/40/50 years. Projections appear only after at least 7 days of recorded data.

## [1.0.0] - 2026-10-18

### Added

- Initial release: counts Instagram Reels upward swipes via an accessibility service, tracks session time via UsageStats, stores everything locally in SQLite.
- Dashboard, History, Limits, and Settings screens, plus onboarding.
- Optional daily target, threshold/session-reminder/quiet-hours notifications, and a floating counter pill (off by default).

### Fixed (1.0.x)

- Stability fixes across the first weeks: startup crashes, zero-count detection, pill auto-show/hide, and a mid-session counting gap.
