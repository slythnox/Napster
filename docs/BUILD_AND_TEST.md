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
| `abi` | `arm64`, `armeabi`, `universal`, `x86`, `x86_64` | Target CPU ABI; `arm64` is standard 64-bit ARM, `universal` builds fat binary |
| `variant` | `foss`, `gms` | `foss` omits Google Play Services / Cast; `gms` includes Cast support |

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

### Release Keystore Signing (Free)

Release APKs require a signing configuration. Android signing keys are free and can be generated locally with `keytool`:

```bash
keytool -genkeypair -v -keystore release.keystore -alias napster -keyalg RSA -keysize 2048 -validity 10000 -storepass YourPassword -keypass YourPassword -dname "CN=Napster,O=Napster,C=US"
```

Place `release.keystore` in `app/keystore/release.keystore` and add credentials to `local.properties`:

```properties
STORE_PASSWORD=YourPassword
KEY_ALIAS=napster
KEY_PASSWORD=YourPassword
```

**For GitHub Actions Automated Releases:**
Add the following repository secrets under **Repository Settings > Secrets and variables > Actions**:
- `RELEASE_KEYSTORE_BASE64`: Base64 encoded `release.keystore` (PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))` | Linux: `base64 -w 0 release.keystore`)
- `STORE_PASSWORD`: Keystore password
- `KEY_ALIAS`: Keystore key alias
- `KEY_PASSWORD`: Key password

If these secrets are not configured, GitHub Actions automatically falls back to a generated debug keystore so builds and workflows always succeed.

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

Unit tests live in `app/src/test/` and cover all core architecture components:

| Test Suite | Location | Coverage |
|---|---|---|
| `AddonClientTest` | `app/src/test/.../addon/` | Manifest discovery, search queries, 500 error handling, HTTP 429 Retry-After backoff, request deduplication via `SharedCalls`, direct `streamURL` bypass, and remix/live rejection. Uses standard in-process Java `HttpServer`. |
| `FlacTaggerTest` | `app/src/test/.../download/` | Rejection of non-FLAC byte streams, safe fallback on corrupt/truncated headers, UTF-8 multilingual metadata preservation, synchronized lyrics, word-timed lyrics embedding, and byte-exact audio frame preservation. |
| `NetworkQualityPreferenceTest` | `app/src/test/.../playback/` | Independent Wi-Fi and mobile resolution, transport precedence over metered Wi-Fi, fallback to global `AudioQualityKey`, Data Saver override, and mid-session transitions. |
| `AudioDiagnosticsTest` | `app/src/test/.../audio/` | Decoder input monitoring, downgrade detection (claimed lossless vs lossy stream), float carrier normalization, and UI badge formatting. |
| `TrackMatcherTest` | `app/src/test/.../addon/` | Track scoring, noise stripping (e.g. "- Topic", "Official Video"), and duration delta penalization. |
| `AddonPolicyTest` | `app/src/test/.../addon/` | Add-on manifest serialization, permissive download defaults, and lossless output requirement gating. |

Run all primary unit tests with:
```powershell
.\gradlew.bat --no-daemon :app:testArm64FossDebugUnitTest
```

---

## Nightly and Release CI

The repository includes GitHub Actions workflows:

| Workflow | File | Trigger | Artifact |
|---|---|---|---|
| Nightly build | `.github/workflows/nightly.yml` | Manual / scheduled | `napster-gms-nightly` |
| Release build | `.github/workflows/release.yml` | On published release | `napster-gms-release` |

Nightly builds accept a `-Pnightly=true` Gradle property that appends the run number to the version name.

---

## Room Schema

Room schema exports are stored in `app/schemas/com.beatwave.music.db.InternalDatabase/`. The current schema version is **37**. When adding a Room entity or column, increment the version, add a migration in `InternalDatabase.kt`, and export the schema by running the build (or the `generateRoomSchemas` task if configured). Never modify existing migration files.

---

## Troubleshooting Builds

See [`docs/TROUBLESHOOTING.md`](TROUBLESHOOTING.md) for common build failures and their resolutions.
