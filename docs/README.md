# Documentation Suite

Welcome to the technical documentation suite for **Napster**, an open-source Android music player engineered with Jetpack Compose, AndroidX Media3 / ExoPlayer, Room, and YouTube Music streaming via InnerTube.

---

## Documentation Index

| Document | Primary Focus |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | High-level architecture, module organisation, system layers, and data flows. |
| [`SETUP.md`](SETUP.md) | Development environment prerequisites, JDK/SDK configuration, and local setup. |
| [`BUILD_AND_TEST.md`](BUILD_AND_TEST.md) | Gradle build variants, compilation tasks, testing commands, and CI workflows. |
| [`FEATURES.md`](FEATURES.md) | Verified feature catalogue, system capabilities, and intentionally excluded features. |
| [`PLAYBACK.md`](PLAYBACK.md) | Media3 foreground service, player lifecycle, queue coordination, caching, and stream resolution. |
| [`SEARCH_AND_SPOTIFY_IMPORT.md`](SEARCH_AND_SPOTIFY_IMPORT.md) | InnerTube search architecture, debouncing, and unauthenticated public Spotify playlist import. |
| [`DATABASE_AND_PREFERENCES.md`](DATABASE_AND_PREFERENCES.md) | Room SQLite database entities, DAOs, schema migrations, and DataStore preferences. |
| [`INTEGRATIONS.md`](INTEGRATIONS.md) | Optional integrations: Last.fm, ListenBrainz, canvas visualisers, and alternative stream providers. |
| [`PRIVACY_AND_PERMISSIONS.md`](PRIVACY_AND_PERMISSIONS.md) | Local data storage, external network flows, device permissions, and zero-telemetry commitments. |
| [`BRANDING_AND_ATTRIBUTION.md`](BRANDING_AND_ATTRIBUTION.md) | Product identity, package namespace rationale, GPL-3.0 licensing, and upstream attribution. |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Architectural invariants, code conventions, testing expectations, and contribution workflow. |
| [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) | Build failure remedies, playback error resolution, and common runtime troubleshooting. |
| [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md) | Step-by-step pre-release verification protocol and artifact compilation guidance. |
| [`APK_UPDATE_AND_SIGNING.md`](APK_UPDATE_AND_SIGNING.md) | Release signing lifecycle, keystore security, certificate fingerprint verification, and safe in-place APK upgrades. |

---

## Core Build Commands

```powershell
# Compile primary ARM64 FOSS development debug binary
.\gradlew.bat --no-daemon :app:assembleArm64FossDebug

# Execute unit test suite
.\gradlew.bat --no-daemon :app:testArm64FossDebugUnitTest

# Install to connected device or emulator
.\gradlew.bat --no-daemon :app:installArm64FossDebug
```
