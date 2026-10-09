# Branding, Licensing & Upstream Attribution

## 1. Product Identity

- **Application name:** Napster
- **Application ID:** `com.napster.music` — as declared in `app/build.gradle.kts`.
- **Internal Kotlin namespace:** `com.beatwave.music` — retained intentionally (see below).
- **Disclaimer:** Napster is an independent open-source project and is not affiliated with, endorsed by, or associated with Google LLC, YouTube, Spotify, or any historical or current commercial Napster entity.

---

## 2. Why `com.beatwave.music` Is Retained

The Kotlin package namespace controls several compatibility-sensitive identifiers:

- **Room schema export path:** All 37 migration files live at `app/schemas/com.beatwave.music.db.InternalDatabase/`. Room uses the fully-qualified class name as a key; renaming the namespace without a validated schema migration would silently break database opening on existing installations.
- **DataStore file names** are derived from the preferences name, which embeds the namespace in several places via Hilt-generated keys.
- **Hilt component class names** (e.g., `DaggerApp_HiltComponents_SingletonC`) are generated from the application class in the current namespace.
- **AndroidManifest activity aliases** reference fully-qualified component names in several intent filters and shortcuts.
- **ProGuard/R8 keep rules** in `proguard-rules.pro` use fully-qualified class names.

Renaming the namespace would require updating all of the above, adding a Room migration, validating serialisation compatibility, and re-testing on real device upgrades. This work has not been performed. Any future migration should be done as an explicit, isolated, fully tested task.

---

## 3. Licence (GNU General Public License v3.0)

Napster is distributed under the **GNU General Public License v3.0 (GPL-3.0)**.

- Full licence text: [`LICENSE`](../LICENSE)
- Any distribution, fork, or modification of this project must also remain open-source under GPL-3.0.
- The GPL-3.0 requires that the complete corresponding source code be made available to recipients of compiled binaries.

---

## 4. Upstream Attribution & Project Lineage

Napster builds upon foundational open-source work from:

| Project | Contribution | Licence |
|---|---|---|
| **BeatWave** | Upstream project basis: media controls, lyrics alignment, theme layouts, Room schema lineage | GPL-3.0 |
| **vivi-music** (Vividh P Ashokan) | Foundational architecture, Apple Music Player UI style | GPL-3.0 |
| **CONVX** | Design inspiration and media layout features | GPL-3.0 |
| **InnerTune** | Original InnerTube API client and early Room schema | GPL-3.0 |

The upstream BeatWave repository is at `github.com/beatlabs790/BeatWave`. Links to this repository in updater code, About screen attribution, and issue templates are accurate upstream attribution and are not replaced with unverified URLs.

---

## 5. Key Open-Source Libraries

| Library | Purpose |
|---|---|
| AndroidX Media3 / ExoPlayer | Core media engine, background playback, audio offloading |
| Jetpack Compose & Material 3 | Declarative UI toolkit |
| Kyant0/backdrop | Hardware-accelerated real-time backdrop blur and refraction |
| AndroidX Room | SQLite object mapping and database persistence |
| AndroidX DataStore | Key-value reactive preference persistence |
| Coil 3 | Image loading, decoding, and caching |
| Dagger Hilt | Dependency injection |
| Timber | Structured logging |
| NewPipe Extractor | Supplementary YouTube stream extraction |

---

## 6. Copyright Headers in Source Files

Kotlin source files carry a brief copyright header of the form:

```kotlin
/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
```

This header refers to the Napster project as a whole. Upstream copyright from InnerTune, vivi-music, and BeatWave contributors is acknowledged here and in the GPL-3.0 `LICENSE` file. The header does not supersede upstream copyright; it identifies this derivative project.

---

## 7. AI-Assisted Development Disclosure

Portions of this project's development — including refactoring, performance improvements, documentation, and test authoring — were assisted by AI tools (Google Gemini and Anthropic Claude). All AI-generated output was reviewed and validated by human contributors before being committed. AI tools are not legal authors and are not credited as contributors. All copyright and licence obligations rest with the respective human authors and the upstream projects listed above.
