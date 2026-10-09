# Database and Preferences Architecture

This document describes the local persistence layer of Napster, including Room SQLite schemas, migrations, Data Access Objects (DAOs), transaction safety, and Jetpack DataStore preferences.

---

## 1. Room SQLite Database (`com.beatwave.music.db`)

Napster relies on AndroidX Room for all structured local persistence. The database definition and configuration are maintained in `InternalDatabase.kt` and injected via Dagger Hilt in `DatabaseModule.kt`.

- **Current Schema Version:** 37
- **Schema Export Directory:** `app/schemas/com.beatwave.music.db.InternalDatabase/`
- **Database File:** `internal.db` within the application private storage directory.

### Core Database Entities

| Entity | Primary Purpose |
|---|---|
| `Song` | Persists track metadata, including YouTube video ID, track title, artists, duration, thumbnail URLs, and play counts. |
| `Album` | Stores album metadata, release year, artist relationships, and cover art references. |
| `Artist` | Stores artist profiles, thumbnail references, and followed status. |
| `Playlist` & `PlaylistSongMap` | Stores custom user playlists and ordered relational mappings of songs to playlist entries. |
| `Event` | Stores timestamped playback events utilised for Daily Discover algorithms, Quick Picks, and listening statistics. |
| `SearchHistory` | Caches recent search queries locally on device. |
| `DownloadEntity` / `FormatEntity` | Tracks offline media segments, formats, file paths, and download completion states. |
| `RecognitionHistory` | **Compatibility entity:** Preserved in the Room schema to ensure that database migrations from earlier application versions remain continuous without requiring schema destruction or complex table migrations. |

### Data Access Object Optimisations (`DatabaseDao.kt`)

1. **Atomic Play-Count Transactions:** The `incrementPlayCount(songId)` operation is an atomic `@Transaction`. It combines `insert(PlayCountEntity(..., 0))` using `OnConflictStrategy.IGNORE` with `incrementPlayCount(songId, year, month)`, entirely eliminating previous `runBlocking` calls and thread starvation risks.
2. **Strictly Bounded Seed Queries:** Queries feeding recommendation algorithms (`recentEvents(limit: Int)`, `mostRecentLikedSong()`, `mostRecentLibrarySong()`) enforce strict SQL `LIMIT` clauses directly in SQLite, avoiding full-table memory dumps.
3. **Lightweight Identification Projections:** List item rows query targeted ID projections (`albumSongIds(albumId)`, `playlistSongIds(playlistId)`) returning `List<String>` rather than constructing thousands of heavyweight `Song` entities just to display an offline badge.

---

## 2. Jetpack DataStore Preferences

Reactive user configuration is maintained via AndroidX DataStore Preferences, defined primarily across `com.beatwave.music.constants.PreferenceKeys`.

### Core Preference Domains

- **Audio & Playback:**
  - Audio quality level (`AudioQualityKey`)
  - Volume normalisation (`AudioNormalizationKey`)
  - Crossfade transition duration (`CrossfadeDurationKey`)
  - Persistent queue state (`PersistentQueueKey`)
  - Sleep timer interval and auto-load more recommendations (`AutoLoadMoreKey`)
- **Appearance & Theme:**
  - Dark mode preference (`DarkModeKey`)
  - Pure Black OLED theme toggle (`PureBlackKey`)
  - Dynamic artwork colour derivation (`DynamicColorKey`)
  - Liquid glass backdrop blur enable (`LiquidGlassKey`)
  - Active theme and layout presets (`ThemePresetKey`)
- **Content & Localization:**
  - Content language (`ContentLanguageKey`) and regional market country code (`ContentCountryKey`)
  - Explicit content filter toggle (`HideExplicitKey`)
- **Privacy & Security:**
  - Pause listening history recording (`PauseListenHistoryKey`)
  - Pause search query caching (`PauseSearchHistoryKey`)
  - Secure window flag / screenshot prevention (`DisableScreenshotKey`)
- **Integrations:**
  - Last.fm session credentials and scrobble state (`LastFmSessionKey`, `LastFmScrobbleKey`)
  - ListenBrainz user token and scrobble enable (`ListenBrainzTokenKey`, `ListenBrainzEnabledKey`)

### Concurrency and Thread Safety

Preferences read within `MusicService` callbacks are mirrored in thread-safe in-memory variables and updated via a background coroutine flow collector. Direct reads from ViewModels execute via non-blocking Kotlin flows (`dataStore.data.first()`), preventing UI frame drops and main-thread deadlocks.
