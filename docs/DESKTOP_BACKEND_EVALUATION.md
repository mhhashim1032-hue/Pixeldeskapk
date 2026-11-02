# Linux graphical desktop backend evaluation

**Evaluation date:** 2026-11-02  
**Target:** Android 9+ ARM64, with Pixel 9 Pro XL (Tensor G4 / Mali) as the primary validation device.

## Executive decision

Do not bundle or redistribute the Termux:X11 APK, its companion package, or copied native components in PIXEL DESK at this stage. Treat Termux:X11 as an optional external backend for a later, user-installed integration prototype. First prove that a PRoot process launched by PIXEL DESK can reach an X server socket with Android's app sandbox and SELinux boundaries intact.

The current app-private workspace `/tmp` is not automatically shared with another Android application. A working desktop requires a deliberately shared socket path or a supported bridge, not merely setting `DISPLAY=:1`.

## Evidence from upstream

The official Termux:X11 project describes itself as an Android NDK X server add-on. Its setup requires both an Android APK and a companion Termux package; the README documents Android 8+ support. For PRoot, upstream instructs users to use `--shared-tmp` or set `TMPDIR` to a path corresponding to the target container's `/tmp`. It also documents the display environment, launching an Xfce session, and device-specific `-legacy-drawing` / `-force-bgra` workarounds.

The upstream README says the project is released under **GPL-3.0**. This is a copyleft license; redistribution or modification requires a careful review of the complete license obligations and corresponding source. PIXEL DESK has not yet selected a repository-wide license, so vendoring or embedding Termux:X11 is blocked pending a licensing decision.

The upstream repository also documents a loader for third-party applications. Its loader checks that the target Termux:X11 package is installed and that its signing signature matches an expected value before loading the target entry point. This is not a generic stable Android intent/API contract and must not be bypassed.

## Integration constraints

1. **Two separately installed components:** users need the X11 Android app and the companion Termux package. Installing only an APK does not provide the `termux-x11` command expected by the documented workflow.
2. **Private storage boundary:** PIXEL DESK stores the Debian rootfs and workspace under its own app-private directory. Another app cannot simply open that directory. A socket under external shared storage is not a safe substitute for a Unix-domain socket directory.
3. **PRoot mount boundary:** our current PRoot launch binds the app's own temporary directory to guest `/tmp`; it does not share Termux's `/tmp` or X11 socket directory.
4. **Process entry point:** upstream's third-party loader is tied to the installed APK's package and signature. Calling undocumented entry points or hardcoding a signing hash would be brittle and unsafe.
5. **Android lifecycle:** the X server and desktop session need independent start/stop handling, foreground/background policy, notifications, and recovery when the external activity is closed.
6. **Graphics:** upstream documents rendering workarounds on some devices. Pixel Mali compatibility must be tested; do not assume Adreno-specific paths work.

## Candidate integration paths

| Path | Advantages | Risks / decision |
|---|---|---|
| Bundle Termux:X11 in PIXEL DESK | One installer, cohesive UX | GPL-3.0 source/license obligations, update/signing ownership, package size. **Blocked.** |
| Depend on separately installed Termux + Termux:X11 | Reuses upstream maintenance | Extra apps, Termux package setup, shared temporary socket and process-launch boundary. **Prototype only.** |
| Build/maintain our own X server backend | Full product control | Significant native graphics, input, lifecycle and security scope. **Not selected.** |

## Required prototype gates

Before marking Linux GUI as implemented:

- Detect whether the official Termux:X11 Android package is installed and its version.
- Provide a user-driven setup guide; do not silently install another APK or run shell-loader code without explicit consent.
- Verify a supported, signature-checked way to start/stop the server. Do not hardcode upstream signature hashes.
- Prove a Unix-domain X11 socket can be shared with the app-private PRoot guest without weakening Android sandboxing or SELinux.
- Start a minimal X client first; then test a small Xfce session.
- Verify touch-to-mouse mapping, keyboard input, rotation, DPI, display resize, app backgrounding and cleanup.
- Record Pixel-specific performance and memory behavior.
- Complete GPL-3.0 and all transitive dependency license review before any bundling or redistribution.

## Sources

- Official upstream README and setup instructions: https://github.com/termux/termux-x11
- Upstream loader implementation: https://github.com/termux/termux-x11/blob/master/shell-loader/src/main/java/com/termux/x11/Loader.java
- Upstream GPL-3.0 license text: https://github.com/termux/termux-x11/blob/master/LICENSE

*This is a technical integration assessment, not legal advice. License obligations should be confirmed before redistribution.*
