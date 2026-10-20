# APK Update, Signing & Release Engineering

This document outlines the release signing architecture, certificate verification protocols, keystore security procedures, and safe in-place APK update workflows for **Napster** (`com.napster.music`).

---

## 1. Architectural Principles & Compatibility Invariants

Because Napster is distributed directly via GitHub Releases and F-Droid rather than Google Play, the Android OS Package Manager (`PackageManager`) enforces signature and version constraints directly on the user's device during updates.

For an existing user to install an update without data loss or installation failures (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), three strict conditions must be satisfied:

1. **Identical Application ID:** The new build must declare `applicationId = "com.napster.music"` in `defaultConfig`. (The Kotlin package namespace `com.beatwave.music` remains internal to the source code to safeguard Room database serialisation).
2. **Matching Signing Certificate:** The new APK must be signed by the identical private key that signed the previously installed APK. If the certificate's SHA-256 fingerprint differs by even one byte, Android terminates installation immediately with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
3. **Monotonically Increasing Version Code:** The new APK's `versionCode` must be strictly greater than the installed APK's `versionCode` (`newVersionCode > oldVersionCode`). Android blocks installation if `newVersionCode <= oldVersionCode` unless the user uninstalls the app first (which destroys all local playlists, database records, and downloads).

```
   Existing Installed APK                       Candidate Update APK
┌────────────────────────────┐               ┌────────────────────────────┐
│ App ID: com.napster.music  │               │ App ID: com.napster.music  │
│ VersionCode: 10000         │ ◄───────────┐ │ VersionCode: 10001 (Must >)│
│ Cert SHA-256: [Fingerprint]│ Identical?  │ │ Cert SHA-256: [Fingerprint]│
└────────────────────────────┘             └─┴────────────────────────────┘
               │                                            │
               └──────────────────┬─────────────────────────┘
                                  │
                     Android OS PackageManager
                                  │
                  ┌───────────────┴───────────────┐
                  ▼                               ▼
     [Signatures & ID Match]             [Mismatch or Downgrade]
                  │                               │
        In-Place Data Upgrade           INSTALL_FAILED_UPDATE_INCOMPATIBLE
 (Preserves DB, DataStore, Audio)               (Installation Rejected)
```

---

## 2. Monotonic Versioning Protocol

Napster maintains a single source of truth for application versions in [`version.properties`](../version.properties):

```properties
VERSION_MAJOR=1
VERSION_MINOR=0
VERSION_PATCH=0
```

### Version Code Derivation Formula
In [`app/build.gradle.kts`](../app/build.gradle.kts), `versionCode` is computed deterministically:
$$\text{versionCode} = (\text{VERSION\_MAJOR} \times 10{,}000) + (\text{VERSION\_MINOR} \times 100) + \text{VERSION\_PATCH}$$

| `versionName` | `VERSION_MAJOR` | `VERSION_MINOR` | `VERSION_PATCH` | Computed `versionCode` |
|---|---|---|---|---|
| `1.0.0` | `1` | `0` | `0` | `10000` |
| `1.0.1` | `1` | `0` | `1` | `10001` |
| `1.1.0` | `1` | `1` | `0` | `10100` |
| `2.0.0` | `2` | `0` | `0` | `20000` |

### Version Bump Workflow
To bump the version before creating a release:
1. Update `version.properties` with the desired increment.
2. Verify Gradle derives the expected code by running:
   ```powershell
   .\gradlew.bat :app:properties | Select-String "versionCode"
   ```
3. Ensure no manual overrides are passed to the build.

---

## 3. Signing Architecture & Keystore Security

### Gradle Configuration
Production release signing configuration is declared in `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("keystore/release.keystore")
        storePassword = localProperties.getProperty("STORE_PASSWORD") ?: System.getenv("STORE_PASSWORD")
        keyAlias = localProperties.getProperty("KEY_ALIAS") ?: System.getenv("KEY_ALIAS")
        keyPassword = localProperties.getProperty("KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD")
    }
}
```

### GitHub Actions Secret Injection
In automated CI/CD ([`.github/workflows/release.yml`](../.github/workflows/release.yml)), credentials and key material are passed strictly via encrypted repository secrets:

- `RELEASE_KEYSTORE_BASE64`: Base64-encoded binary content of the production `.keystore` / `.jks` file.
- `STORE_PASSWORD` or `RELEASE_KEYSTORE_PASSWORD`: Keystore master passphrase.
- `KEY_ALIAS` or `RELEASE_KEY_ALIAS`: Keystore alias name.
- `KEY_PASSWORD` or `RELEASE_KEY_PASSWORD`: Key private passphrase.

The workflow reconstructs `app/keystore/release.keystore` transiently during the runner container's execution and deletes it when the runner terminates.

### Local Signing Setup
For local production builds:
1. Place your release keystore at `app/keystore/release.keystore` (this path is ignored by git in `.gitignore`).
2. Add the following to your private `local.properties` (never committed to git):
   ```properties
   STORE_PASSWORD=your_keystore_password
   KEY_ALIAS=your_key_alias
   KEY_PASSWORD=your_key_password
   ```
3. Build the release binary:
   ```powershell
   .\gradlew.bat :app:assembleUniversalGmsRelease
   ```

### Keystore Protection & Backup Protocol
> [!CAUTION]
> If the production release keystore is lost, existing users **cannot** receive updates without uninstalling the app and erasing their local libraries, playlists, and cached audio. There is no recovery mechanism for a lost signing key.

1. **Off-Site Cold Storage:** The release `.keystore` file must be stored in at least two encrypted off-site locations (e.g., hardware security key, encrypted offline cold storage drive, or password manager vault).
2. **Never Commit Secrets:** `.gitignore` includes `*.keystore`, `keystore/`, `app/keystore/`, and `local.properties`. Never use `git add -f` on any keystore file.
3. **Least Privilege:** Do not share keystore passwords in chat channels, issue trackers, or documentation.

---

## 4. Certificate Fingerprint Verification Guide

Before uploading an APK to a GitHub Release or distributing it to users, verify that its certificate signature matches the installed production certificate.

### Step 1: Extract Existing Certificate Fingerprint
Locate the Android SDK build-tools directory (e.g., `$ANDROID_HOME/build-tools/35.0.0/` or `34.0.0/`). Run `apksigner` on your previously published production APK:

```powershell
# Windows PowerShell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs "path\to\Napster-v1.0.0.apk"
```

```bash
# Linux / macOS
apksigner verify --verbose --print-certs path/to/Napster-v1.0.0.apk
```

**Expected Output:**
```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Number of signers: 1
Signer #1 certificate DN: ...
Signer #1 certificate SHA-256 digest: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
Signer #1 certificate SHA-1 digest: ...
```

### Step 2: Verify the Candidate Update APK
Run the identical command on the newly generated APK:

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs "app\build\outputs\apk\universalGms\release\Napster-universal-gms-release.apk"
```

### Step 3: Compare Fingerprints
Compare the `Signer #1 certificate SHA-256 digest` strings.
- **Match:** The update is cryptographically compatible and safe to distribute. Android will perform a seamless in-place upgrade.
- **Mismatch:** The update was signed with a different key (e.g., debug keystore). **Do not distribute.** Investigate CI/local signing credentials immediately.

### Step 4: Verify APK Version Code
Verify that `versionCode` has strictly increased using `aapt2`:

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\aapt2.exe" dump badging "app\build\outputs\apk\universalGms\release\Napster-universal-gms-release.apk" | Select-String "versionCode"
```

---

## 5. In-Place Update Data Preservation Guarantees

When an APK update satisfies the cryptographic and version requirements above, the Android Package Manager performs an atomic in-place upgrade preserving:

1. **Room SQLite Database (`music.db`):** All 37 sequential schema migrations are preserved. Playlists, history, liked tracks, offline metadata, and artist relationships remain intact.
2. **DataStore Preferences:** All user settings, equalisation presets, audio quality preferences, and UI theme configurations are preserved.
3. **Downloaded Media:** Offline cached audio files residing in `context.filesDir` (managed by `ExoDownloadService`) remain intact across updates.
4. **Coil Image Disk Cache:** Artwork thumbnails cached in `filesDir/coil/` persist across updates, avoiding network refetching on first launch.

---

## 6. Future In-App Updater Proposal (Technical RFC)

> [!NOTE]
> This section is an architectural RFC and design evaluation. In accordance with release stability and security best practices, automatic APK downloading/updating is not currently enabled in the binary.

### Overview
Because Napster does not rely on Google Play, users currently check GitHub Releases manually. A future in-app update checker can notify users of new releases and facilitate verified APK downloads.

### Recommended System Architecture

```
┌─────────────────────────────────┐
│     Napster Client App          │
└──────────────┬──────────────────┘
               │ 1. GET /repos/slythnox/Napster/releases/latest
               ▼
┌─────────────────────────────────┐
│     GitHub REST API             │
│ (ETag & Cache-Control Headers)  │
└──────────────┬──────────────────┘
               │ 2. Release JSON: tag_name, assets, SHA-256 checksums
               ▼
┌─────────────────────────────────┐
│  Version & Asset Resolution     │
│  - Compare tag version code     │
│  - Detect ABI (arm64, universal)│
└──────────────┬──────────────────┘
               │ 3. Download APK to app cache directory
               ▼
┌─────────────────────────────────┐
│ Cryptographic Verification      │
│  - Verify SHA-256 asset hash    │
│  - Inspect PackageManager cert  │
└──────────────┬──────────────────┘
               │ 4. PackageInstaller Session API
               ▼
┌─────────────────────────────────┐
│ Android OS Installation Dialog  │
└─────────────────────────────────┘
```

### Core Design Requirements

1. **Rate Limiting & Bandwidth Protection:**
   - Unauthenticated GitHub API calls are limited to 60 requests per hour per IP.
   - Use HTTP conditional requests with `If-None-Match` (ETag) stored in DataStore. If no new release exists, GitHub responds with `304 Not Modified` (0 bandwidth, 0 API quota penalty).
   - Check only on explicit user request or at most once every 24 hours on Wi-Fi.

2. **Architecture-Aware Asset Matching:**
   - Napster builds both architecture-specific APKs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) and `universal` APKs.
   - The client must inspect `android.os.Build.SUPPORTED_ABIS` and select the optimal APK asset rather than downloading a heavy universal build.

3. **Cryptographic Integrity Verification:**
   - Releases should publish a signed `checksums.txt` containing SHA-256 hashes of all release artifacts.
   - The app must compute `MessageDigest.getInstance("SHA-256")` on the downloaded file before invoking installation.
   - If the hash does not match, the file is immediately discarded.

4. **Android Modern Installation Dispatch:**
   - Target SDK is 36. Use Android's modern `PackageInstaller` session API rather than legacy `ACTION_VIEW` intent with `FileProvider`.
   - `PackageInstaller.Session` allows streaming the APK directly to the package manager and provides atomic progress reporting and graceful error callbacks (`STATUS_FAILURE_CONFLICT`, `STATUS_FAILURE_STORAGE`).
