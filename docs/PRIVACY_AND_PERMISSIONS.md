# Privacy, Network Flows and Android Permissions

This document provides a source-verified overview of how Napster handles local data storage, network traffic, third-party services, and Android device permissions.

---

## 1. Zero Telemetry and Privacy Commitments

- **Zero Analytics SDKs:** Napster does not integrate Google Analytics, Firebase Analytics, Crashlytics, Mixpanel, Segment, or any other tracking framework.
- **Zero Advertising Trackers:** The application contains no advertising libraries, user-tracking beacons, or behavioural profiling daemons.
- **Device-Bound Diagnostics:** Crash traces and logcat outputs remain strictly within the user's private device storage (`cache/napster_crash_*.txt`) and are never dispatched to external servers automatically.

---

## 2. Local Data Storage

All persistent application data resides within the private Android sandbox (`/data/data/com.napster.music/`):
- **Room SQLite Database (`internal.db`):** Stores song metadata, album and artist records, user-created playlists, listening history timestamps, search query logs, and offline download references.
- **DataStore Preferences:** Stores boolean configuration flags, theme customisation choices, and optional service authentication tokens.
- **ExoPlayer Cache and Downloads:** Cached audio chunks and full offline tracks are stored in application-private storage or external scoped directories (`Android/data/com.napster.music/files/`).

---

## 3. Network Flows and Remote Endpoints

| Remote Endpoint | Transport | Data Transmitted | Engineering Purpose |
|---|---|---|---|
| **YouTube Music (InnerTube)** | HTTPS (`music.youtube.com`, `googlevideo.com`) | Search query terms, YouTube video IDs, streaming parameters | Stream URL resolution, search results, track metadata, and album artwork. |
| **Spotify Public Embed Scraper** | HTTPS (`open.spotify.com/embed/playlist/...`) | 22-character Spotify Playlist ID (anonymously) | Scrapes public HTML metadata for track matching; no credentials sent. |
| **Last.fm** (Optional) | HTTPS (`ws.audioscrobbler.com/2.0/`) | Track title, artist name, playback timestamp, session key | Submits scrobble events and now-playing status. |
| **ListenBrainz** (Optional) | HTTPS (`api.listenbrainz.org/1/submit-listens`) | Track title, artist name, playback timestamp, user token | Submits listens to the open MusicBrainz database. |
| **GitHub Releases API** (Optional) | HTTPS (`api.github.com/repos/...`) | Client version string | Inspects published release tags for in-app updates. |
| **Lyrics Providers** (Optional) | HTTPS (KuGou, LRCLIB, Musixmatch, Better Lyrics) | Track title, artist name, track duration | Queries time-synchronised and plain text lyrics. |
| **Canvas Providers** (Optional) | HTTPS (Apple Music, Tidal, Echo) | Track and artist identifiers | Fetches looping background video artwork. |

---

## 4. Android Device Permissions

| Permission | API Level | Engineering Rationale |
|---|---|---|
| `POST_NOTIFICATIONS` | API 33+ (Android 13+) | Required to display the foreground playback notification containing media controls in the system notification shade and on the lock screen. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | API 26+ | Required by Android to run `MusicService` in the foreground, guaranteeing continuous playback when the application is minimised or the display is powered down. |
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | API 26+ | Required solely to index and play local audio files selected by the user from device storage. |
| `WAKE_LOCK` | All | Prevents system CPU sleep states during active audio decoding and stream playback. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | All | Enables streaming audio chunks and fetching album metadata over Wi-Fi and mobile networks. |
| `REQUEST_INSTALL_PACKAGES` | API 26+ (GMS build only) | Optional permission allowing the in-app updater on GMS variants to prompt user confirmation for downloaded APK upgrades. |
