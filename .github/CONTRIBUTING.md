# 🤝 Contributing to Napster

Thanks for considering a contribution to **Napster**. This document describes the current development process.

## 🛠️ Project Layout

Napster is a Gradle multi-module Android Studio project. The pieces you'll actually touch:

* **`app/`** — the app itself. Compose UI, feature packages, playback, core data, and integrations live under `app/src/main/kotlin/com/beatwave/music/`.
* **`ui/component/GlassEffect.kt`** + **`ui/component/backdrop/`** — the Liquid Glass system (a vendored, source-included copy of [Kyant0/backdrop](https://github.com/Kyant0/backdrop)). `Modifier.liquidGlass(...)` is the entry point most UI code needs; see the doc comment on `GlassCircleButton` for a worked example of a glass surface sampling a local backdrop.
* **`innertube/`** — the unofficial YouTube Music (InnerTube) API client, kept independent of the app module.
* Other top-level modules (`kugou`, `lrclib`, `lastfm`, `betterlyrics`, `simpmusic`, `youlyplus`, `spotify`, and provider modules) are individually-scoped integrations. Keep changes scoped to the relevant module.

## 🖥️ Building locally

* **JDK 21**, latest stable **Android Studio**.
* `compileSdk 37`, `minSdk 26` — install those platforms via the SDK Manager if Android Studio prompts you to.
* Clone and open the project in Android Studio, let Gradle sync, then run/debug the `app` module as normal (`./gradlew :app:assembleUniversalFossDebug` from the CLI works too). No manual native toolchain, submodules, or codegen scripts are required — Gradle handles everything, including the protobuf codegen used by a couple of modules.

## 🌿 Branches & Commits

* Branch names: `feature/short-description`, `fix/short-description`.
* Commit messages: a short type prefix (`feat:`, `fix:`, `refactor:`, `docs:`) followed by an imperative summary — see `git log` for the house style. Explain *why* in the body when it isn't obvious from the diff.

## 🚀 Pull Requests

1. Fork the repo and branch off `main`.
2. Keep the diff scoped to the PR's stated purpose — avoid drive-by reformatting or unrelated refactors, they make review harder.
3. Actually run the change on a device/emulator before opening the PR; this is a UI-heavy app and most regressions here are visual, not compile-time.
4. Describe *what* changed and *why* in the PR description; screenshots/recordings are expected for anything UI-visible.

## 🐞 Reporting Bugs

Open an [issue](https://github.com/slythnox/Napster/issues) with repro steps, your Android version/device, and `adb logcat` output if it's a crash. A screen recording is helpful for visual or playback regressions.

## 💬 Questions

Open an issue or start a GitHub Discussion before starting anything large — happy to point you at the right file instead of you reverse-engineering it.
