# Cleanup and Architectural Refactoring History

This document provides a concise engineering record of cleanup and refactoring decisions across project phases, explaining the technical rationale for each architectural excision.

---

## 1. Custom Backend and Listen Together (Phase 8)

- **Status:** Completely removed.
- **Rationale:** Napster is structured as a client-side Android music player without dedicated servers. Synchronised listening rooms introduced external WebSocket connections, cloud hosting dependencies, and maintenance overhead.
- **Actions Executed:**
  - Excised the entire `feature/listentogether/` directory (all 9 classes: manager, WebSocket client, protocol models, etc.).
  - Removed `ListenTogetherOverlay.kt` and `ListenTogetherSettings.kt`.
  - Cleared Listen Together menu entries and UI actions across 12 menu and player components.
  - Removed `LocalListenTogetherManager` and its Hilt dependency injection provider.
  - Purged obsolete preference keys (`ListenTogether*Key`) and string resources (`listen_together_*`).

---

## 2. Private Spotify APIs and Account Scrapers (Phase 8)

- **Status:** Private account and GraphQL APIs purged; public playlist import active.
- **Rationale:** Private Spotify endpoints required brittle WebView cookie harvesting and reverse-engineered GraphQL tokens prone to frequent breakage.
- **Actions Executed:**
  - Excised `SpotifyHashProvider.kt` and authenticated GraphQL endpoints (`me`, `myPlaylists`, `playlistTracks`, `likedSongs`).
  - Purged `SpotifyLoginDialog` and WebView cookie-harvesting stubs from `SpotifyScreen.kt` and `WelcomeActivity.kt`.
  - Retained the standalone `:spotify` module (`PublicSpotifyMetadataClient`, `fetchPlaylistEmbedTracks`, `SpotifyMapper`) exclusively for unauthenticated public playlist parsing and track matching.

---

## 3. Microphone Audio Recognition (Phase 8)

- **Status:** User-facing recognition removed; database schema entity retained.
- **Rationale:** Audio fingerprinting required microphone recording permissions (`RECORD_AUDIO`) and external recognition servers.
- **Actions Executed:**
  - Removed runtime recognition services, permission requests, and UI navigation items.
  - Retained the `RecognitionHistory` Room entity and DAO queries in the database layer to maintain database migration continuity across existing user installations.

---

## 4. Discord Rich Presence Daemon (Phase 8)

- **Status:** Completely removed.
- **Rationale:** Desktop-oriented presence daemons (Kizzy) running persistent background IPC created unnecessary battery consumption and maintenance friction.
- **Actions Executed:** Excised Discord IPC background services whilst retaining public community support links in `AboutScreen.kt`.

---

## 5. Performance and Concurrency Enhancements (Phase 9)

- **Status:** Completed and verified.
- **Rationale:** Synchronous preference reads on the main thread during ExoPlayer state changes caused micro-stutters and ANR risks.
- **Actions Executed:**
  - Replaced blocking DataStore reads in `MusicService.kt` with thread-safe in-memory cached mirrors updated via a single coroutine collector.
  - Added 300 ms debouncing to search suggestion keystroke flows.
  - Converted Room play-count mutations to an atomic `@Transaction`, eliminating blocking queries.
  - Implemented scoped metadata fetching across fallback stream resolvers in `ExternalStreamProviderResolver.kt`.
  - Added early terminal exit in `BotDetectionMitigator.kt` to abort 12-client retry loops on deleted or restricted tracks.

---

## 6. Settings and Legacy Surface Excisions (Phases 10 & 10.5)

- **Status:** Completed and verified.
- **Rationale:** Unused legacy pages (Account screen, BeatWave Story screen) and lingering upstream brand names were inconsistent with the unified Napster experience.
- **Actions Executed:**
  - Excised obsolete `feature/account/` directory and settings entry points.
  - Relocated ListenBrainz scrobbling directly to `IntegrationScreen.kt`.
  - Replaced legacy theme identifiers (`Theme.BeatWave` -> `Theme.Napster`) across styles, layouts, and manifests.
  - Updated payment payee metadata (`pn=Napster` in `Donation.kt`), picture export directories (`/Napster` in `ComposeToImage.kt`), and preset formats (`.napsterpreset`).
  - Preserved internal Kotlin package namespace `com.beatwave.music` for Room database and DataStore compatibility, whilst maintaining application ID `com.napster.music`.
