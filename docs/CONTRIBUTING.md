# Contributing to Napster

Thank you for your interest in contributing to Napster. This guide outlines architectural constraints, engineering standards, and the pull request workflow.

---

## 1. Architectural Invariants

Before submitting changes, ensure your contributions adhere to these non-negotiable principles:

1. **Preserve Internal Namespace:** Do not alter the Kotlin namespace `com.beatwave.music` or the application ID `com.napster.music`. Compatibility with existing Room databases and DataStore preferences relies upon these exact identifiers.
2. **Room Database Integrity:** Never modify existing Room migration files (versions 1 through 37). When altering the schema, increment the version number, author a migration step in `InternalDatabase.kt`, and export the new JSON schema. Compatibility entities such as `RecognitionHistory` must remain untouched.
3. **Non-Blocking Main Thread:** Avoid `runBlocking` calls on the main thread or within `Player.Listener` callbacks in `MusicService.kt`. Maintain in-memory cached state and observe DataStore streams asynchronously.
4. **Resilient Integrations:** Optional integrations (scrobbling, lyrics providers, alternative stream resolvers) must fail gracefully without disrupting audio playback, stalling the queue, or crashing the process.
5. **Zero Telemetry Policy:** Do not introduce analytics SDKs, advertising beacons, or remote tracking daemons.
6. **UI and Liquid Glass Patterns:** Employ existing component primitives (`Modifier.liquidGlass`, `Material3SettingsGroup`, `AppleTokens`) to maintain visual cohesion across Jetpack Compose layouts.

---

## 2. Engineering Workflow

1. **Branching:** Create a descriptive branch from `main`:
   ```bash
   git checkout -b feature/short-description
   ```
2. **Atomic Commits:** Make focused changes accompanied by concise, imperative commit messages explaining the technical rationale.
3. **Pre-Submission Validation:** Execute the test suite and assemble the primary debug variant locally:
   ```powershell
   .\gradlew.bat --no-daemon :app:testArm64FossDebugUnitTest
   .\gradlew.bat --no-daemon :app:assembleArm64FossDebug
   ```
4. **Pull Request Submission:** Open a pull request providing reproduction steps, device specifications, and visual demonstrations (screenshots or recordings) for any user-facing UI modifications.
