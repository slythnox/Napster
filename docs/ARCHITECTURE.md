# Architecture

## Overview

Napster is an Android music player built with Jetpack Compose, Media3/ExoPlayer, AndroidX Room, AndroidX DataStore, and Dagger Hilt. It connects to YouTube Music via the unofficial InnerTube API and optionally integrates with Last.fm, ListenBrainz, Spotify (public playlists only), and several canvas and lyrics providers.

- **Application ID:** `com.napster.music`
- **Internal Kotlin namespace:** `com.beatwave.music` — retained to preserve Room database migration identity, DataStore serialisation keys, and Hilt component bindings. Changing it would require a validated database migration and full serialisation audit; this has not been performed.
- **Gradle root project name:** `napster`

---

## Module Structure

| Module | Purpose |
|---|---|
| `app/` | Application module — UI, features, playback, Room, DataStore, Hilt |
| `innertube/` | Unofficial YouTube Music (InnerTube) API client |
| `canvas/` | Tidal and Apple Music canvas provider abstractions |
| `applecanvas/` | Apple Music canvas (artwork + video) provider |
| `vivimusiccanvas/` | Echo / Vivi Music canvas provider |
| `artistvideo/` | Artist video canvas provider |
| `betterlyrics/` | Better Lyrics API client |
| `kugou/` | KuGou lyrics client |
| `lrclib/` | LRCLib lyrics client |
| `paxsenixlyrics/` | Paxsenix lyrics client |
| `youlyplus/` | YouLyPlus lyrics client |
| `musixmatchlyrics/` | Musixmatch lyrics client |
| `simpmusic/` | SimpMusic integration helper |
| `lastfm/` | Last.fm and ListenBrainz scrobbling client |
| `spotify/` | Public Spotify metadata client (no auth) |
| `jiosaavn/` | JioSaavn optional stream provider |
| `spine/` | Spine optional stream provider |

---

## Package Layout (inside `app/`)

```
com.beatwave.music/
├── app/           – Application, MainActivity, WelcomeActivity, CrashActivity
├── constants/     – Preference keys, donation URIs, feature flags
├── db/            – Room database, entities, DAOs, migrations
├── di/            – Hilt modules and component bindings
├── feature/       – One sub-package per screen/feature
│   ├── home/
│   ├── search/
│   ├── library/
│   ├── player/
│   ├── lyrics/
│   ├── settings/  – Settings, About, Privacy, Updates, Integrations
│   ├── local/
│   ├── download/
│   ├── history/
│   ├── spotify/   – Spotify public playlist import
│   └── wrapped/
├── download/      – FLAC file metadata tagging (FlacTagger) and offline export
├── integration/   – Last.fm, ListenBrainz, updater, Cast, Tidal, JioSaavn, Spine
├── models/        – Shared data models (MediaMetadata, QueueData, etc.)
├── playback/      – MusicService, PlayerConnection, queue, events, DJ
│   ├── audio/     – Audio diagnostics, output route tracking, Bluetooth A2DP telemetry
│   └── resolver/  – Stream resolution pipeline & HTTP add-on sources (AddonClient, TrackMatcher)
├── ui/
│   ├── component/ – Reusable Compose components
│   ├── navigation/
│   ├── overlays/
│   ├── theme/
│   └── utils/
├── utils/         – DataStore helpers, YTPlayerUtils, SongExporter, cipher, potoken, preset
└── widget/        – App widgets (Dot Player, Lyrics Player, Vinyl Deck)
```

---

## Data Flow

### Playback

```
Feature screen / ViewModel
  → PlayerConnection (Compose ↔ Service bridge)
    → MusicService (Media3 ExoPlayer, MediaSession, notifications)
      → playback/resolver/ → YTPlayerUtils / ExternalStreamProviderResolver
        → innertube/ (InnerTube API) or external provider (Spine, Tidal, JioSaavn)
      → queue/ (ListenTogether-free queue state)
      → events/ → PlaybackEventDispatcher → Last.fm / ListenBrainz
```

`MusicService` owns the ExoPlayer instance, MediaSession, audio focus, notification, background wakelock, cache coordination, download management, and the optional DJ mixer. Preference values consumed inside player callbacks are cached in-memory (`cachedRepeatMode`, `cachedPersistentQueue`, etc.) and updated reactively via a single DataStore flow collector — no `runBlocking` calls remain on the main thread.

### Spotify Public Playlist Import

```
Public Spotify playlist URL
  → playlist ID extraction
  → PublicSpotifyMetadataClient  (spotify/ module)
  → SpotifyMapper
  → DeterministicTrackResolutionEngine
    → YouTube search via innertube/
  → resolved Song entities
  → Room database (playlist + song persistence)
```

No Spotify account login, WebView cookie harvesting, or private API access is involved.

---

## Key Design Decisions

### Namespace retained as `com.beatwave.music`

Room exports its schema under the fully-qualified class name of `InternalDatabase`. All 37 migrations in `app/schemas/com.beatwave.music.db.InternalDatabase/` would become stale if the namespace were renamed without a corresponding schema migration. DataStore preference files are also keyed by this namespace. Renaming without a full migration audit would silently corrupt or lose user data on upgrade.

### Optional integrations and add-ons must not break core playback

Every optional provider (HTTP add-ons, Spine, Tidal, JioSaavn, canvas providers, lyrics sources) is wired to fail silently. `ExternalStreamProviderResolver` falls back to YouTube if all external providers fail. `PlaybackEventDispatcher` catches exceptions from scrobbling clients individually. HTTP add-ons enforce client-side cooldowns (HTTP 429 Retry-After) and deduplicate in-flight requests (`SharedCalls`) to prevent cascading failures.

### Truth-first audio telemetry without unverified bit-perfect claims

Audio diagnostics monitor the actual decoder format via Media3 `AnalyticsListener.onAudioInputFormatChanged()` and physical routes via Android `AudioManager` and `BluetoothProfile`. Telemetry highlights downstream downgrades honestly. The app avoids unsupported promises of "bit-perfect" playback, acknowledging that audio output routes through standard Android AudioTrack/AudioFlinger system pipelines.

### Non-destructive FLAC tagging and export

`FlacTagger` manipulates FLAC metadata blocks (Vorbis comments, pictures, padding) while copying raw audio frames byte-for-byte untouched. Non-FLAC sources are exported as M4A/AAC rather than falsely relabeled as FLAC.

### Recognition history retained despite feature removal

The `RecognitionHistory` Room entity and its DAO methods remain in the schema to preserve database migration continuity. The user-facing recognition UI and microphone permission were removed in Phase 8. Dropping the entity would require a schema migration that either drops the table (losing history data) or performs a no-op migration — neither is automatically safe.

---

## Removed Features

The following were removed in Phases 7–8 and no longer exist in source or navigation:

| Feature | Status |
|---|---|
| Listen Together / collaborative rooms | Completely removed (source, DI, UI, strings, tests) |
| Discord Rich Presence (Kizzy) | Completely removed |
| Spotify private account APIs / login WebView | Completely removed |
| AI lyrics translation / AI playlist modification | Completely removed |
| Shazam / microphone recognition (UI) | Removed; DB entity retained |
| Supabase / custom backend services | Completely removed |
| Legacy Account screen | Completely removed |
| Legacy Story page | Completely removed |

---

## Known Complexity Hotspots

The following files are intentionally large and are not candidates for refactoring in a cleanup phase:

- `MusicService.kt` — broad Android service, ExoPlayer, MediaSession, notifications, audio focus, cache, DJ.
- `DatabaseDao.kt` — single DAO for the entire Room database; reflects a deliberate monolithic DAO design for query locality.
- `YTPlayerUtils.kt` — InnerTube stream resolution with 12-client fallback, SABR/cipher deobfuscation, po-token generation.
- `MainActivity.kt` — Compose host, navigation, global state, and many integration hooks.
