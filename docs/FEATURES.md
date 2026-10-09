# Feature Catalogue

This document provides a comprehensive inventory of verified features available in Napster, alongside an explicit record of intentionally excluded legacy capabilities.

---

## 1. Online Streaming and Discovery

- **YouTube Music Streaming:** Ad-free audio stream resolution using the unofficial InnerTube API client.
- **Home Feed:** Dynamically generated Quick Picks, Daily Discover, Trending Charts, Moods and Genres, and New Releases.
- **Background Playback:** Persistent background playback managed via a foreground service when the application is minimised or the display is locked.
- **High-Resolution Artwork:** High-quality album art and artist thumbnails fetched and cached asynchronously via Coil 3.
- **Alternative Stream Providers:** Optional fallback streams via Spine, Tidal, or JioSaavn when configured by the user.

---

## 2. Search and Spotify Playlist Import

- **Debounced Instant Search:** Real-time query suggestion mechanism with a 300 ms debounce, preventing redundant InnerTube network calls during rapid keystrokes.
- **Category Filter Chips:** Direct filtering across Songs, Videos, Albums, Artists, and Featured Playlists.
- **Public Spotify Playlist Import:** Direct URL parsing (`open.spotify.com/playlist/...`) extracting playlist track metadata via public embed scrapers, followed by deterministic track matching on YouTube Music without requiring user credentials.

---

## 3. Audio Engine and Playback Capabilities

- **AndroidX Media3 / ExoPlayer Core:** Robust foreground service architecture (`MusicService.kt`) coordinating MediaSession controls, system lock-screen notifications, and Android Auto integration.
- **DJ Mode and Transitions:** Automated beat-matched transitions, harmonic key detection, and energy profiling across sequential queue items.
- **Audio Equaliser:** Built-in multi-band graphical equaliser featuring preset audio profiles (Bass Boost, Vocal, Rock, Pop) and custom user curves.
- **Playback Polish:** Seamless gapless playback, volume normalisation, configurable crossfade durations, sleep timers, and persistent queue restoration across application restarts.
- **Synchronised Lyrics:** Word-by-word highlighted karaoke lyrics with multiple prioritised fallback providers (KuGou, LRCLIB, Musixmatch, Better Lyrics, Paxsenix).

---

## 4. Local Media and Library Management

- **On-Device Audio Indexing:** Automatic scanning and playback of local audio files stored on device storage (`READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`).
- **Unified Library:** Creation of custom local playlists, favourited songs, bookmarked albums, and followed artists.
- **Offline Downloads and Caching:** Pre-caching and dedicated downloading of streamed tracks for offline listening, constrained by user-configurable storage budgets.
- **Tag Inspection:** External tag editor delegation for ID3 metadata inspection.

---

## 5. User Interface and Theming

- **Liquid Glass Interface:** Real-time hardware-accelerated backdrop blur and refractive frosted glass surfaces powered by Kyant0/backdrop.
- **Dynamic Theming:** Instant palette derivation from current song artwork or system Material You dynamic colours.
- **Pure Black Mode:** OLED black background configuration for power efficiency and high contrast.
- **Theme Presets:** One-tap presets (Deep Midnight OLED, Cyberpunk Neon, Emerald Frosted Glass, Rose Gold, Sunset Amber, Dynamic Material You) with export/import support via `.napsterpreset` archives.
- **Homescreen Widgets:** Four dedicated app widget layouts (Compact, Now Playing Row/Square/Wide, Synchronised Lyrics, and Turntable).

---

## 6. Privacy and Data Governance

- **Zero Telemetry:** No analytics SDKs, advertising trackers, or telemetry daemons exist within the codebase.
- **Local Persistence:** All playlists, listening records, and library bookmarks reside strictly on device storage within SQLite via Room.
- **Privacy Controls:** Independent toggles to pause or clear playback history, search history, and temporary network caches.

---

## 7. Intentionally Excluded Legacy Features

The following features were present in early upstream projects (BeatWave, CONVX, vivi-music) and have been permanently removed:

| Feature | Engineering Rationale for Removal |
|---|---|
| **Listen Together / Social Rooms** | Required external WebSocket servers and custom backend infrastructure. Napster operates as a client-only player without remote servers. |
| **Private Spotify Authentication** | Required brittle WebView cookie harvesting and private GraphQL account APIs. Replaced with unauthenticated public playlist parsing. |
| **Shazam / Microphone Recognition** | Required microphone permissions (`RECORD_AUDIO`) and external fingerprinting daemons. UI removed; database schema entity retained for migration integrity. |
| **Discord Rich Presence (Kizzy)** | Deprecated daemon requiring background IPC and third-party tokens. |
| **AI Playlist Modification & Translation** | Proprietary remote LLM endpoints with high latency and external dependencies. |
| **Supabase / Custom Backend Accounts** | Remote account synchronisation eliminated in favour of local-first data ownership. |
| **Obsolete Account & Story Screens** | Unreferenced user-facing remnants excised in Phase 10. |
