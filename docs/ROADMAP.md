# PIXEL DESK roadmap

## M0 — Repository and product foundation
- [x] Kotlin + Jetpack Compose Android application and CI.
- [x] Home, Environments, Files, Terminal, Apps and Settings navigation.
- [ ] Automated UI smoke tests.

## M1 — Linux ARM64 userspace
- [x] Package a research Android ARM64 PRoot candidate and startup/version probe.
- [x] Secure app-private workspace layout and bounded archive extraction.
- [x] Pin Debian 12 Bookworm ARM64 rootfs with SHA-256 and size limits.
- [x] Download, extract and validate Debian metadata and ARM64 shell ELF.
- [x] Add bounded PRoot command execution with exit status, output limits, timeout and cancellation.
- [x] Add a persistent non-PTY shell with command history, reconnect and cleanup.
- [x] Configure Debian resolver file from active Android DNS servers.
- [x] Add APT update, install, remove and cache-clean operations.
- [x] Add curated starter package catalog, installed-package inventory and basic diagnostics.
- [ ] Validate PRoot startup, rootfs install, shell commands, DNS and APT on a physical Pixel.
- [ ] Add a real PTY for terminal control sequences, Ctrl+C and full-screen applications.
- [ ] Complete PRoot/dependency license review before production redistribution.
- [ ] Add environment lifecycle controls: create, start, stop and delete.
- [ ] Add backup/export and restore.

## M2 — Linux graphical desktop
- [ ] Evaluate Termux:X11 and alternative display servers, including licensing.
- [ ] Implement desktop session lifecycle and display configuration.
- [ ] Integrate keyboard, mouse, clipboard and touch input.
- [ ] Validate a minimal desktop on Pixel 9 Pro XL with Mali GPU.

## M3 — Linux application experience
- [x] APT package install/remove and installed-package inventory.
- [ ] Discover installed GUI applications and their desktop entries.
- [ ] Launch compatible GUI applications from PIXEL DESK.
- [ ] Add per-app compatibility metadata and diagnostic logs.

## M4 — Windows compatibility research
- [ ] Evaluate Wine and CPU translation feasibility on Tensor G4 / Mali.
- [ ] Build a compatibility probe before installer execution.
- [ ] Prototype one redistributable test application.
- [ ] Publish device-specific limitations; do not promise universal EXE support.

## M5 — Virtualization research
- [ ] Evaluate QEMU feasibility, acceleration, memory overhead and Android restrictions.
- [ ] Verify hardware acceleration before advertising VM support.
- [ ] Keep VM support optional and isolated.

## M6 — Security, QA and releases
- [x] Unit tests for workspace, import, downloader, extraction, command runner, shell, package validation and DNS formatting.
- [x] CI debug APK artifacts and build/test/lint checks.
- [ ] Physical-device integration and UI tests.
- [ ] Threat model and complete dependency/license inventory.
- [ ] Signed release workflow after signing secrets are configured.

## Completion policy

A screen or roadmap entry is not a completed feature. Mark a capability complete only after implementation and documented tests. Separate emulator validation from physical-device validation. A successful CI build does not prove that PRoot, Debian, networking, APT or graphical applications work on a physical device.
