# Native PRoot runtime research

## Status

This is an isolated prototype track. No PRoot executable is bundled into the PIXEL DESK APK, and no Linux environment is advertised as working until device validation passes.

## Candidate build

- Source: [berilevi/proot-static](https://github.com/berilevi/proot-static)
- Pinned source commit: `6f8ebfd8e24887dfba64c3f2d7d5fe9dc059b60e`
- Target: Android ARM64 / `arm64-v8a`
- Build toolchain: Android NDK `28.2.13676358`
- Build entry point: `tools/build-static-aarch64.sh`
- Expected outputs: `proot`, `loader`, and optionally `loader32`

The workflow is deliberately separate from Android app CI. It produces a short-lived research artifact and retains the checked-out source and build output so the binary can be inspected. It checks the primary executable and loader ELF architecture, checks the primary PRoot executable has no dynamic `NEEDED` entries, and records SHA-256 checksums and source provenance.

## Licensing and provenance gates

The candidate source identifies itself as a Termux-patched PRoot copy. PRoot source files use GPL version 2 or (at the recipient's option) any later version. The bundled Talloc source includes LGPL-3.0 and GPL-3.0 license texts. Static linking creates additional LGPL compliance obligations; the workflow therefore retains source and intermediate build inputs for review. This research build is not approved for redistribution inside the app until a complete dependency/license audit and the applicable relinking/source requirements are satisfied.

A production build should prefer a maintained upstream source and a documented, reproducible dependency graph. Never replace this pinned source with an unpinned branch or download a prebuilt executable without verifying its origin and digest.

## Android integration gates

Before adding binaries to `app/src/main/jniLibs/arm64-v8a/`:

1. Verify the binary is ARM64 Android/Bionic-compatible and works on the minimum supported Android API.
2. Determine the exact PRoot loader configuration and pass its absolute path explicitly.
3. Confirm Android packaging extracts executable native payloads into `ApplicationInfo.nativeLibraryDir`; ordinary app-private workspace files are not assumed executable.
4. Add native payloads only with their exact license notices and required corresponding source/relinkable inputs.
5. Add an instrumentation test that launches `proot --version` and captures stdout, stderr and exit code.
6. Test a minimal Debian ARM64 rootfs on a physical Pixel device, including `/bin/sh -c 'uname -m'`.
7. Keep rootfs data under app-private storage and use PRoot link-to-symlink behaviour where required by Android filesystem restrictions.

## References

- [Termux execution environment](https://github.com/termux/termux-packages/wiki/Termux-execution-environment)
- [Termux PRoot package build recipe](https://github.com/termux/termux-packages/blob/master/packages/proot/build.sh)
- [Static PRoot build candidate](https://github.com/berilevi/proot-static)
