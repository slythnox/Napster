# Build and Test

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | 21 |
| Android Studio | Ladybug (2024.2) or later |
| Android SDK platform | API 26 (minSdk) and API 37 (compileSdk) |
| NDK | `27.0.12077973` (configured in `app/build.gradle.kts`; Gradle installs it automatically if NDK is enabled in the SDK Manager) |
| Gradle | Managed by the wrapper (`gradlew` / `gradlew.bat`) — do not install separately |

---

## Build Variants

The `app` module declares two product flavours:

| Flavour dimension | Values | Notes |
|---|---|---|
| `abi` | `arm64`, `universal` | `arm64` targets 64-bit ARM devices; `universal` includes all ABIs |
| `store` | `foss`, `gms` | `foss` omits Google Play Services (Cast, GMS); `gms` includes them |

Combined with the standard `debug` / `release` build types, the primary variants used in development are:

| Variant | Task suffix | Use |
|---|---|---|
| arm64 FOSS Debug | `assembleArm64FossDebug` | Primary development and CI validation |
| arm64 FOSS Release | `assembleArm64FossRelease` | FOSS signed release |
| Universal FOSS Debug | `assembleUniversalFossDebug` | Multi-ABI FOSS debug |
| Universal GMS Release | `assembleUniversalGmsRelease` | Play Store / nightly CI build |

---

## Common Gradle Commands

Run all commands from the repository root on Windows:

```powershell
# Primary debug build (ARM64, FOSS)
.\gradlew.bat --no-daemon :app:assembleArm64FossDebug

# Unit tests for primary variant
.\gradlew.bat --no-daemon :app:testArm64FossDebugUnitTest

# All FOSS debug variants
.\gradlew.bat --no-daemon :app:assembleFossDebug

# Clean build (use if caches seem stale)
.\gradlew.bat --no-daemon clean :app:assembleArm64FossDebug

# Universal GMS release (nightly CI target)
.\gradlew.bat --no-daemon assembleUniversalGmsRelease -Pnightly=true

# Install on connected device
.\gradlew.bat --no-daemon :app:installArm64FossDebug
```

---

## Configuration Files

### `local.properties`

This file is not committed. Create it at the repository root:

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
LASTFM_API_KEY=your_key
LASTFM_SECRET=your_secret
```

Last.fm keys are optional; the build succeeds without them (the keys default to empty strings and Last.fm features are disabled at runtime if unconfigured).

### Signing

Release builds require a signing configuration. The `app/build.gradle.kts` reads signing parameters from `local.properties` or environment variables:

```properties
STORE_FILE=path/to/keystore.jks
STORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

Alternatively, set the equivalent environment variables in CI.

---

## Running Tests

```powershell
# Unit tests — all modules
.\gradlew.bat --no-daemon test

# Unit tests — app module, primary variant only
.\gradlew.bat --no-daemon :app:testArm64FossDebugUnitTest

# Lint (runs on all variants; may be slow)
.\gradlew.bat --no-daemon :app:lint
```

Unit tests live in `app/src/test/`. There are no instrumented (on-device) tests in the current configuration.

---

## Nightly and Release CI

The repository includes GitHub Actions workflows:

| Workflow | File | Trigger | Artifact |
|---|---|---|---|
| Nightly build | `.github/workflows/nightly.yml` | Manual / scheduled | `napster-gms-nightly` |
| Release build | `.github/workflows/release.yml` | On published release | `napster-gms-release` |
| Discord notification | `.github/workflows/discord-notify.yml` | On published release | — |

Nightly builds accept a `-Pnightly=true` Gradle property that appends the run number to the version name.

---

## Room Schema

Room schema exports are stored in `app/schemas/com.beatwave.music.db.InternalDatabase/`. The current schema version is **37**. When adding a Room entity or column, increment the version, add a migration in `InternalDatabase.kt`, and export the schema by running the build (or the `generateRoomSchemas` task if configured). Never modify existing migration files.

---

## Troubleshooting Builds

See [`docs/TROUBLESHOOTING.md`](TROUBLESHOOTING.md) for common build failures and their resolutions.
