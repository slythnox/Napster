# Development Environment Setup

This document describes the prerequisites and configuration required to establish a development environment for the Napster Android application.

---

## 1. Prerequisites

- **Operating System:** Windows 10/11, macOS (12+), or Linux (Ubuntu 22.04 LTS or newer).
- **JDK:** Java Development Kit 21 or 17 (Eclipse Temurin or OpenJDK recommended).
- **Android SDK:**
  - `compileSdk`: 37
  - `targetSdk`: 36
  - `minSdk`: 26 (Android 8.0 Oreo)
  - `ndkVersion`: `27.0.12077973` (installed automatically via the Android SDK Manager when NDK components are enabled)
- **Build System:** Gradle wrapper (`gradlew.bat` on Windows, `./gradlew` on Unix-like environments).
- **IDE:** Android Studio Ladybug (2024.2.1+) or newer.

---

## 2. Environment Variables

Ensure that your `JAVA_HOME` and `ANDROID_HOME` variables point to your installed toolchains:

### Windows PowerShell

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.x"
$env:ANDROID_HOME = "C:\Users\<username>\AppData\Local\Android\Sdk"
$env:PATH += ";$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin"
```

### Linux / macOS

```bash
export JAVA_HOME="/usr/lib/jvm/temurin-21-jdk"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"
```

---

## 3. Local Configuration (`local.properties`)

The project reads optional local secrets and the SDK directory from `local.properties` in the repository root. This file is excluded from version control.

Example `local.properties`:

```properties
sdk.dir=C\:\\Users\\<username>\\AppData\\Local\\Android\\Sdk

# Optional: Last.fm API credentials (scrobbling disabled if omitted)
LASTFM_API_KEY=
LASTFM_SECRET=
```

---

## 4. Environment Verification

To confirm that your development toolchain is operational, execute the following commands from the repository root:

```powershell
.\gradlew.bat --no-daemon --version
.\gradlew.bat --no-daemon :app:assembleArm64FossDebug
```

For subsequent build and test instructions, refer to [`docs/BUILD_AND_TEST.md`](BUILD_AND_TEST.md).
