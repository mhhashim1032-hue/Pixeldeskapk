# PIXEL DESK roadmap

## M0 — Repository and product foundation
- [x] Confirm repository access and empty starting state.
- [x] Establish product scope and compatibility caveats.
- [x] Verify first Android CI build.

## M1 — Native Android shell
- [x] Kotlin + Jetpack Compose application.
- [x] Home, Environments, Files, Terminal, Apps and Settings navigation.
- [x] Honest environment status labels.
- [ ] Add automated UI smoke tests.

## M2 — Linux ARM64 environment
- [x] Document Android-native PRoot packaging candidates.
- [x] Pin and build a research PRoot ARM64 candidate with an Android NDK workflow.
- [x] Package the candidate and corresponding source/build inputs in development APKs.
- [ ] Complete license review and physical-device startup validation.
- [x] Add bounded, staged TAR.GZ extraction with path, symlink and hard-link checks.
- [x] Add a pinned Debian 12 Bookworm ARM64 rootfs catalog with SHA-256 and size limits.
- [x] Implement rootfs download, bounded extraction, Debian metadata and ARM64 ELF validation.
- [ ] Verify the real rootfs download/extraction and first shell command on a physical Pixel.
- [x] Implement download integrity verification (resumable downloads remain open).
- [x] Implement environment lifecycle: create, start, stop, delete.
- [x] Add native PRoot startup/version probe with timeout and bounded output.
- [x] Add bounded non-interactive PRoot command execution with exit code, stdout/stderr, timeout and cancellation handling.
- [x] Add a persistent non-PTY shell with command history, reconnect and lifecycle cleanup.
- [ ] Validate the persistent shell against the real Debian rootfs on a physical Pixel.
- [x] Add an Android ARM64 POSIX PTY backend with byte I/O, terminal sizing, terminal echo/line discipline and process-group signals.
- [x] Add a bounded Compose VT renderer for common cursor movement, erase/scroll, ANSI SGR colors, alternate screen and incremental UTF-8.
- [ ] Expand terminal protocol compatibility (grapheme width, mouse, bracketed paste and advanced xterm modes).
- [x] Configure guest DNS from Android active-network DNS servers.
- [x] Add safe APT update, install, remove and cache-clean operations.
- [x] Implement safe app-private workspace storage layout.
- [x] Implement streamed environment backup/export with a format manifest, symlink-safe traversal and a compressed-archive SHA-256 digest.
- [ ] Verify on a physical ARM64 Android device.

## M3 — Linux graphical desktop
- [x] Evaluate Termux:X11 integration, app/process boundaries and GPL-3.0 redistribution requirements.
- [ ] Prototype optional external X11 backend with explicit user setup and secure shared-socket validation.
- [ ] Evaluate Termux:X11 integration and licensing/redistribution requirements.
- [ ] Desktop session lifecycle and display configuration.
- [ ] Keyboard, mouse and clipboard integration.
- [ ] Validate a minimal desktop on the target Pixel.

## M4 — Application library
- [ ] Package install/uninstall flows within managed Linux environment.
- [ ] Add package inventory and installed-app discovery.
- [ ] Application discovery and launch.
- [ ] Per-app compatibility metadata and logs.

## M5 — Windows compatibility research
- [ ] Evaluate Wine and Box64 integration feasibility on Tensor G4 / Mali.
- [ ] Build a compatibility probe before installer execution.
- [ ] Prototype one redistributable test application.
- [ ] Publish device-specific limitations.

## M6 — Virtualization research
- [ ] Evaluate QEMU feasibility, acceleration and memory overhead.
- [ ] Do not advertise hardware virtualization until verified.
- [ ] Keep VM support optional and isolated.

## M7 — Security, QA and releases
- [ ] Threat model and dependency/license inventory.
- [x] Unit tests for workspace, import, downloader, rootfs extraction, command runner and persistent shell fixtures.
- [ ] Physical-device integration tests for PRoot, DNS and APT.
- [ ] Integration and UI tests.
- [x] CI debug APK artifacts.
- [ ] Signed release workflow after signing secrets are configured.

## Completion policy

A screen or roadmap entry is not a completed feature. Mark a capability complete only after implementation and documented tests. Separate emulator validation from physical-device validation.
