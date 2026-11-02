# Native PRoot runtime research

## Status

A pinned Android ARM64 PRoot candidate is built and included in development APK CI. The Settings screen has a native startup/version probe. The Environments screen can install a pinned Debian 12 Bookworm ARM64 rootfs, but no guest shell has yet been launched on a physical device; no graphical desktop or Windows application can be launched yet.

## Candidate build

- Source: [berilevi/proot-static](https://github.com/berilevi/proot-static)
- Pinned source commit: `6f8ebfd8e24887dfba64c3f2d7d5fe9dc059b60e`
- Target: Android ARM64 / `arm64-v8a`
- Build toolchain: Android NDK `28.2.13676358`
- Build entry point: `tools/build-static-aarch64.sh`
- Outputs: `proot`, `loader`, and `loader32`

`scripts/prepare-proot-native.sh` rebuilds the pinned source, places the 64-bit PRoot executable and loader in `app/src/main/jniLibs/arm64-v8a/`, and embeds license notices plus the corresponding source and build intermediates in the APK assets. The 32-bit loader is retained in the corresponding-source archive but is not placed in the `arm64-v8a` ABI directory; 32-bit guest execution is not currently supported.

The APK uses legacy native-library packaging so Android extracts the native payload into `ApplicationInfo.nativeLibraryDir`. `ProotRuntimeProbe` checks the ARM64 ABI and payload files, then starts PRoot with an explicit `PROOT_LOADER` path and its `-V` version option. The process runs off the UI thread with a timeout and bounded output capture.

## Validation status

- [x] Build static Android ARM64 PRoot with pinned source and NDK.
- [x] Verify PRoot and loader ELF architecture and static linkage in the prototype workflow.
- [x] Package native executable, loader, license notices and corresponding source/build inputs in development APKs.
- [x] Add a bounded native startup/version probe and host-side unit tests.
- [ ] Install the development APK on a physical Pixel device and verify the probe succeeds.
- [x] Add a pinned Debian 12 Bookworm ARM64 rootfs installer with digest and ELF validation.
- [ ] Install the rootfs on a physical Pixel and run `/bin/sh -c 'uname -m'`.
- [ ] Test process cancellation, app backgrounding, storage exhaustion and cleanup on-device.
- [ ] Complete dependency/license review before any production release.

## Licensing and provenance

The candidate source identifies itself as a Termux-patched PRoot copy. PRoot source files use GPL version 2 or (at the recipient's option) any later version. The bundled Talloc source includes LGPL-3.0 and GPL-3.0 license texts. The APK includes these notices and a corresponding-source archive containing the pinned source tree and relinkable build inputs. The exact source revision and NDK version are recorded in the provenance file.

The payload is currently for development/device validation only. Production redistribution remains blocked until the dependency inventory, source/relinking materials and all license obligations have been reviewed.

Never replace the pinned source with an unpinned branch or download a prebuilt executable without verifying its origin and digest.

## Android integration gates

1. Verify the native startup probe succeeds on the target Pixel.
2. Keep all rootfs, home and temporary paths inside app-private storage.
3. Pass the loader path explicitly through `PROOT_LOADER`; do not rely on the build machine's embedded fallback path.
4. Use PRoot link-to-symlink handling where required by Android filesystem restrictions.
5. Do not advertise guest command execution until the real ARM64 shell command succeeds.
6. Do not advertise graphical desktop, Windows compatibility or virtualization until each has separate device validation.

## References

- [Termux execution environment](https://github.com/termux/termux-packages/wiki/Termux-execution-environment)
- [Termux PRoot package build recipe](https://github.com/termux/termux-packages/blob/master/packages/proot/build.sh)
- [Static PRoot build candidate](https://github.com/berilevi/proot-static)
