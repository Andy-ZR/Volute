# Volute

An Android terminal emulator and Linux environment — a fork of the **Google Play build lineage of [Termux](https://github.com/termux/termux-app)**, maintained for everyday use on a phone.

> ⚠️ **Unofficial.** This is **not** an official Termux release and is not affiliated with, authorized by, or endorsed by the Termux project or its maintainers. "Termux" is used here only to state the origin of the code.
>
> Base: [`termux-play-store/termux-apps`](https://github.com/termux-play-store/termux-apps)

## What is this

Termux is an Android terminal emulator plus a full Linux user space (bash, coreutils, `apt`/`pkg`, …) running on top of the Android kernel. This fork starts from the **Google Play branch** of it (the variant that complies with current Play requirements — e.g. it targets a modern API level and uses `proot` so downloaded binaries can still be executed on Android 10+).

## Changes in this fork

Relative to the upstream base above:

1. **The soft keyboard comes back when you tap the terminal.**
   With any program that enables mouse reporting (`tmux` with `mouse on`, `opencode`, `htop`, `vim` with `mouse=a`, …), upstream suppresses the soft keyboard on tap *and* forwards that tap to the program as a mouse click — so once you hide the keyboard you can never bring it back by tapping. This fork re-shows the keyboard **when it is hidden**, and still forwards the click.
2. **One-tap session switching.**
   Two extra-keys buttons (`⏮` / `⏭`) switch between terminal sessions without opening the session drawer.
3. **Swipe to switch sessions.**
   Swipe left/right on the terminal to move between sessions (left → next, right → previous). Vertical swipes still scroll; text selection is unaffected.

## Build

Requirements: **JDK 21+**, **Android SDK platform 37**, **NDK 30.0.16248370** (the native build uses `-std=c23`).

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew :termux-app:assembleDebug
# → termux-app/build/outputs/apk/debug/termux-app-debug.apk
```

The build downloads the bootstrap archives from the upstream package repositories at build time (checksum-verified).

## Install

Requires **Android 11+** (minSdk 30). The APK is a debug build signed with the repository's **public test key** — do **not** distribute it as-is, and do not use it to replace another Termux build (different signing keys cannot be installed over each other).

## License

**GPLv3 only**, with exceptions: code from [Android Terminal Emulator](https://github.com/jackpal/Android-Terminal-Emulator) (Apache-2.0) is used in `terminal-view` and `terminal-emulator` — see `LICENSE.md`. Because this is a fork, the complete corresponding source of any distributed build is available in this repository.
