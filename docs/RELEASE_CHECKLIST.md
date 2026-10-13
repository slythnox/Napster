# Release Readiness Checklist

This document provides a factual, step-by-step verification checklist required prior to tagging or publishing a release of Napster.

---

## 1. Functional Verification

- [ ] **Unified Branding:** Verify that the launcher icon, adaptive icon, splash screen, top app bar, widgets, and settings screens display "Napster" consistently without residual fork titles.
- [ ] **Settings Navigation Integrity:** Verify that every destination in `SettingsScreen.kt` navigates correctly and that deleted legacy routes (e.g. Account, Legacy Story, Listen Together) are completely inaccessible.
- [ ] **Privacy Disclosures:** Confirm that `AboutScreen.kt` and `PrivacySettings.kt` accurately reflect data storage and external network flows without fabricated telemetry claims.
- [ ] **Public Playlist Ingestion:** Confirm that valid public Spotify and YouTube Music playlist URLs import successfully, resolve track titles, and populate the local Room database.
- [ ] **Offline Media Playback:** Download an audio track, activate flight mode, and verify offline playback and notification controls operate without network access.
- [ ] **Lock Screen and MediaSession Controls:** Validate play, pause, skip, seek, and artwork rendering within the system media notification shade and lock-screen controls.
- [ ] **Audio Focus Behaviour:** Confirm playback ducks or pauses during phone calls or incoming notifications and resumes reliably upon focus restoration.

---

## 2. Compilation and Test Validation

Execute the unit test suite and assemble the primary binary variant:

```powershell
# Mandatory unit tests
.\gradlew.bat --no-daemon :app:testArm64FossDebugUnitTest

# Primary development assemble
.\gradlew.bat --no-daemon :app:assembleArm64FossDebug
```

---

## 3. Versioning and Changelog Maintenance

1. **Update `version.properties`:**
   - Increment `appVersionCode` (monotonically increasing integer).
   - Set `appVersionName` (e.g. `6.0.0`).
2. **Review `changelog.json`:**
   - Document notable user-facing modifications, performance enhancements, and bug resolutions.
   - Avoid references to excised features or third-party proprietary backends.

---

## 4. Release Binary Compilation

Compile the target signed release APKs:

```powershell
# FOSS Release Binaries
.\gradlew.bat --no-daemon :app:assembleArm64FossRelease
.\gradlew.bat --no-daemon :app:assembleUniversalFossRelease

# GMS Release Binaries
.\gradlew.bat --no-daemon :app:assembleArm64GmsRelease
.\gradlew.bat --no-daemon :app:assembleUniversalGmsRelease
```
