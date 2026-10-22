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
- **Homescreen Widgets:** Three dedicated app widget designs (Napster Dot Player 2×2, split-panel Napster Lyrics Player 4×2, and Napster Vinyl Deck 4×2).

---

## 6. Privacy and Data Governance

- **Zero Telemetry:** No analytics SDKs, advertising trackers, or telemetry daemons exist within the codebase.
- **Local Persistence:** All playlists, listening records, and library bookmarks reside strictly on device storage within SQLite via Room.
- **Privacy Controls:** Independent toggles to pause or clear playback history, search history, and temporary network caches.

---

---

## 7. Advanced Audio Diagnostics and Output Route Telemetry

- **Truth-First Media3 Input Monitoring:** Inspects actual decoder input format parameters directly from Media3 `AnalyticsListener.onAudioInputFormatChanged()`. Reports true codec MIME type, bit depth (16-bit, 24-bit, 32-bit float), sample rate (e.g. 44.1 kHz, 48 kHz, 96 kHz), and bitrate.
- **Audio Chain Downgrade Detection:** Evaluates provider-claimed stream quality against actual observed input stream parameters. Dynamically flags streams when a source claiming lossless Hi-Fi is downgraded to lossy Opus/AAC or downsampled by the pipe.
- **Hardware Output Route Classification:** Listens to `AudioDeviceCallback` and identifies active physical audio output sinks (Internal Speaker, 3.5mm Wired Headset/Line-out, USB Audio/DAC, HDMI/eARC, Audio Dock).
- **Bluetooth A2DP Codec Telemetry:** Authoritative listener for Android Bluetooth A2DP profile state, detecting negotiated Bluetooth codecs (LDAC, aptX HD, aptX Adaptive, AAC, SBC, Samsung Seamless Codec) and reporting sample rate and bit depth.
- **Technical Limitations:** Telemetry reflects information reported by Android system APIs (`AudioDeviceInfo` and `BluetoothCodecStatus`). Standard Android audio routing resamples audio through the system AudioFlinger/AudioTrack mixer unless exclusive USB host mode is enabled. No promises of "bit-perfect" or hardware bit-exact bypass are made. Route detection runs purely as non-blocking telemetry and never gates playback.

---

## 8. HTTP Streaming Add-on Support

- **Open JSON Streaming Protocol:** Allows connecting modular, user-configured HTTP streaming servers implementing the standard add-on specification (`/manifest.json`, `/search?q=...`, `/stream/{id}`).
- **Manifest Introspection & Capabilities:** Automatically queries and caches add-on metadata, declared resources (`search`, `stream`), quality tier mappings (`LOSSLESS`, `HIGH`, `LOW`), and download policies.
- **Resilient Request Deduplication:** In-flight concurrent requests for identical queries are deduplicated via `SharedCalls`, preventing duplicate network queries to self-hosted add-on instances.
- **Rate Limit & Retry-After Backoff:** Full compliance with HTTP 429 responses and `Retry-After` headers, enforcing client-side cooldown timers without hammering servers.
- **Silent Replacement Guard:** Multi-tiered scoring via `TrackMatcher` strictly rejects live recordings, acoustic variations, or remixes when the user requested studio album tracks, preventing silent track mismatch.
- **Direct Stream URL Optimization:** When an add-on search result includes a direct `streamURL`, the resolver uses it directly without a redundant round-trip `/stream/{id}` query.

---

## 9. Lossless FLAC Export and Metadata Tagging

- **Direct Cache-to-Storage Export:** Exports cached lossless audio tracks directly to user-selected locations using Android's Storage Access Framework (`ActivityResultContracts.CreateDocument`).
- **Vorbis Comment & Artwork Tagging:** Uses `FlacTagger` to embed standard FLAC metadata blocks (TITLE, ARTIST, ALBUM, front cover picture block, synchronized LRC lyrics, and word-timed lyrics) directly into the exported file.
- **Audio Frame Preservation:** Guarantees that raw audio frame payloads are copied byte-for-byte untouched without re-encoding, transcoding, or data alteration.
- **Honest Format Handling:** If an audio stream was sourced from a lossy container (YouTube Opus/AAC), the exporter does not disguise or label it as FLAC; it cleanly remuxes/transcodes the track to `.m4a` format.
- **Block Replacement Without Bloat:** Re-tagging an already-tagged FLAC strips and replaces existing Vorbis comment, picture, and padding blocks rather than duplicating metadata blocks.

---

## 10. Per-Network Quality Preferences & Cache Controls

- **Network-Aware Stream Resolution:** Independent audio quality selectors for Wi-Fi and Cellular networks, with support for `Prefer Lossless`, `High Quality`, `Auto`, and `Data Saver`.
- **Transport vs Metered Classification:** Uses active `NetworkCapabilities` transport flags (`TRANSPORT_WIFI`, `TRANSPORT_CELLULAR`), safely falling back to `isActiveNetworkMetered` when transport is ambiguous (e.g. metered mobile hotspots).
- **Dynamic Handover Adaptation:** Listens to `onCapabilitiesChanged` via `NetworkConnectivityObserver` to seamlessly switch quality when transitioning between Wi-Fi and cellular mid-playback without interrupting playback when resolved quality is unchanged.
- **Data Saver Override:** Enabling Data Saver immediately enforces lowest bitrate (`AudioQuality.LOW`) across all networks.
- **Storage Management:** Dedicated settings for monitoring player cache and download cache sizes with instant one-tap cache eviction.

## 11. Intentionally Excluded Legacy Features

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
| **Bit-Perfect / Bit-Exact Output Claims** | Unsupported on standard Android audio architectures without proprietary kernel DAC bypass. Audio is routed cleanly through ExoPlayer and Android AudioTrack. |
| **Wear OS, Android TV, & Desktop Ports** | Scope is strictly focused on high-performance Android mobile and automotive (Android Auto) form factors. |
