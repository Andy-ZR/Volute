# Volute

An Android terminal emulator and Linux environment — a fork of the **Google Play build lineage of [Termux](https://github.com/termux/termux-app)**, maintained for everyday use on a phone.

> ⚠️ **Unofficial.** This is **not** an official Termux release and is not affiliated with, authorized by, or endorsed by the Termux project or its maintainers. "Termux" is used here only to state the origin of the code.
>
> Base: [`termux-play-store/termux-apps`](https://github.com/termux-play-store/termux-apps)
>
> **Status:** personal fork, built and used daily on one phone. No support commitment — see
> [CONTRIBUTING.md](CONTRIBUTING.md).

## What is this

Termux is an Android terminal emulator plus a full Linux user space (bash, coreutils, `apt`/`pkg`, …) running on top of the Android kernel. This fork starts from the **Google Play branch** of it (the variant that complies with current Play requirements — e.g. it targets a modern API level and uses `proot` so downloaded binaries can still be executed on Android 10+).

## Changes in this fork

Modified since **2026-10**, relative to the upstream base above:

1. **The soft keyboard comes back when you tap the terminal.**
   With any program that enables mouse reporting (`tmux` with `mouse on`, `opencode`, `htop`, `vim` with `mouse=a`, …), upstream suppresses the soft keyboard on tap *and* forwards that tap to the program as a mouse click — so once you hide the keyboard you can never bring it back by tapping. This fork re-shows the keyboard **when it is hidden**, and still forwards the click.
2. **Box-drawing and block elements are drawn by the terminal, not the font.**
   TUI frames lose their vertical lines when a font's glyph does not fill the cell. This fork draws `U+2500–257F` and `U+2580–259F` from cell geometry (single/double/heavy/rounded lines, quadrants, fractional blocks). Powerline `U+E0B0–E0B3` is only drawn when the font has no glyph for them, so a patched font keeps its own design.
3. **Volute branding.**
   App name, icon (launcher / shortcut / notification), user-facing strings and the first-run banner (`motd` of the user space, replaced only while it is still the upstream text).

Session switching stays where it is in the base: open the drawer and pick a session from the list.

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

> **Package identity is still `com.termux`.** Only the user-visible name, icon and strings are renamed; the application id, Java packages, permission names and the `termux-*` command names are intentionally unchanged (scripts and companion apps depend on them). This build therefore **cannot coexist with another Termux build on the same device** — installing it replaces that app's identity.

## License

**GPLv3 only**, with exceptions: code from [Android Terminal Emulator](https://github.com/jackpal/Android-Terminal-Emulator) (Apache-2.0) is used in `terminal-view` and `terminal-emulator`.

- Full license text: [`LICENSE`](LICENSE) (GPLv3) and [`LICENSE-APACHE-2.0`](LICENSE-APACHE-2.0) (the exception) — see [`LICENSE.md`](LICENSE.md) for the mapping.
- Because this is a fork, the complete corresponding source of any distributed build is this repository at the released tag.
