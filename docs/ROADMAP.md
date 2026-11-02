# PIXEL DESK roadmap

## M0 — Repository and product foundation
- [x] Confirm repository access and empty starting state.
- [x] Establish product scope and compatibility caveats.
- [ ] Verify first Android CI build.

## M1 — Native Android shell
- [x] Kotlin + Jetpack Compose application.
- [x] Home, Environments, Library, Settings navigation.
- [x] Honest environment status labels.
- [ ] Add automated UI smoke tests.

## M2 — Linux ARM64 environment
- [ ] Select and document a maintainable PRoot distribution bootstrap strategy.
- [x] Add bounded, staged TAR.GZ extraction with path and symlink checks.
- [ ] Add a pinned rootfs catalog and verify a real distribution archive end-to-end.
- [ ] Implement download integrity verification and resumable downloads.
- [ ] Implement environment lifecycle: create, start, stop, delete.
- [ ] Implement safe local storage layout and backup/export.
- [ ] Verify on a physical ARM64 Android device.

## M3 — Linux graphical desktop
- [ ] Evaluate Termux:X11 integration and licensing/redistribution requirements.
- [ ] Desktop session lifecycle and display configuration.
- [ ] Keyboard, mouse and clipboard integration.
- [ ] Validate a minimal desktop on the target Pixel.

## M4 — Application library
- [ ] Package install/uninstall flows within managed Linux environment.
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
- [ ] Unit, integration and UI tests.
- [ ] CI debug APK artifacts.
- [ ] Signed release workflow after signing secrets are configured.

## Completion policy

A screen or roadmap entry is not a completed feature. Mark a capability complete only after implementation and documented tests. Separate emulator validation from physical-device validation.
