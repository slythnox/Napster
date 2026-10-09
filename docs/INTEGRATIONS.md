# Integrations Architecture

This document describes all verified optional third-party integrations in Napster, including scrobbling protocols, alternative stream resolution, canvas providers, lyrics engines, and updater mechanisms.

---

## 1. Architectural Philosophy

All integrations are strictly optional augmentations to the core streaming experience. The following invariants govern all integration implementations:
1. **Zero Playback Disruption:** Failure, timeout, rate-limiting, or invalid credentials within any integration must never interrupt, delay, or terminate active playback.
2. **Graceful Fallback:** When a provider cannot fulfill a request, the system degrades silently or displays a non-blocking toast warning, reverting automatically to primary YouTube Music resolution.
3. **Local Independence:** The application maintains full offline and local library utility even when all external integration endpoints are unreachable.

---

## 2. Scrobbling Integrations

### Last.fm (`lastfm/` module)
- **Protocol:** Standard Last.fm Web Services API v2.0.
- **Configuration Screen:** `settings/integrations/lastfm` (`LastFMSettings.kt`).
- **Data Flow:** Sends immediate "Now Playing" updates upon track commencement, followed by scrobble submission once a track plays for at least 50% of its duration or 4 minutes.
- **Credential Storage:** User session keys are persisted securely within AndroidX DataStore (`LastFmSessionKey`).

### ListenBrainz (`IntegrationScreen.kt`)
- **Protocol:** ListenBrainz JSON REST API (`/1/submit-listens`).
- **Configuration:** Accessible directly under `Settings > Integrations`.
- **Functionality:** Submits scrobbles to the community-driven MetaBrainz ecosystem.
- **Credential Storage:** User API tokens reside in `ListenBrainzTokenKey`.

---

## 3. Alternative Audio Stream Providers (`ExternalStreamProviderResolver.kt`)

Users can optionally configure alternative stream resolution providers for higher bitrate or alternate audio mastering:
- **Tidal / JioSaavn / Spine:** Optional provider clients queried according to user-configured priority rankings.
- **Scoped YouTube Metadata Sharing:** To avoid repeated network latency, metadata from YouTube is resolved once lazily (`getYtMetadata()`) and shared across all candidate fallback providers.
- **Resilient Fallback:** Any HTTP 403, network timeout, or metadata mismatch automatically diverts the playback request to the primary YouTube Music stream without stalling ExoPlayer.

---

## 4. Synchronised Lyrics Providers

Lyrics are queried asynchronously according to user-customised priority rankings across multiple independent modules:
- **KuGou (`kugou/`):** High-precision time-synchronised and translated lyrics.
- **LRCLIB (`lrclib/`):** Open-source, community-governed synchronised lyrics database.
- **Musixmatch (`musixmatchlyrics/`):** Fallback provider for mainstream commercial releases.
- **Better Lyrics (`betterlyrics/`):** TTML formatted synchronised lyrics.
- **Paxsenix (`paxsenixlyrics/`):** Additional open API fallback.
- **InnerTube:** Native timed and plain text lyrics extracted directly from YouTube Music video payloads.

---

## 5. Visual Canvas Providers

The player UI supports dynamic background video loops and canvas animations:
- **Apple Music Canvas (`applecanvas/`):** Fetches official artist video loops and motion artwork.
- **Tidal Canvas (`canvas/`):** Resolves animated track visuals from Tidal CDN endpoints.
- **Echo / Vivi Music Canvas (`vivimusiccanvas/`):** Dynamic canvas video playback.
- **Artist Video (`artistvideo/`):** YouTube-based looping artist background visualisers.

---

## 6. GitHub Release Updater (`vivimusicupdater.kt`)

- **Endpoints:**
  - Stable releases: `api.github.com/repos/slythnox/Napster/releases`
  - Nightly builds: `api.github.com/repos/slythnox/Napster/actions/workflows/nightly.yml/runs`
- **Functionality:** Polls release tags, downloads changelog manifests (`changelog.json`), and presents update prompt dialogues.
- **Package Installation:** On GMS build variants, enqueues background downloads via `WorkManager` (`UpdateDownloadWorker`) and triggers the system package installer via `FileProvider`.
