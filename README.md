# PIXEL DESK

**A native Android desktop workspace for running compatible Linux and Windows software on Android.**

PIXEL DESK targets ARM64 Android devices, with the Google Pixel 9 Pro XL as the primary optimization and validation target.

## Current status

The repository now contains a native Kotlin/Jetpack Compose app, a pinned Debian 12 Bookworm ARM64 rootfs installer, a bundled Android ARM64 PRoot candidate, a persistent shell, environment lifecycle controls, verified TAR.GZ environment backup/export, Android-derived guest DNS configuration, an APT software manager, secure archive extraction and GitHub Actions build workflows.

**Current limitation:** the terminal now has a native ARM64 POSIX PTY backend for interactive byte input/output, terminal sizing and process-group Ctrl+C. Its native process-spawn path and PRoot integration still require physical Pixel validation. The current screen does not yet render ANSI/VT cursor movement, colors or alternate-screen applications, so full-screen tools such as `vim` and `htop` may still need compatibility work because this is a scoped VT subset, not a full xterm implementation. The graphical desktop, Windows executables and virtual machines are not working features yet.

## Runtime roadmap

1. **Linux ARM64:** local userspace environment, initially using a PRoot-based approach.
2. **Linux GUI:** desktop session and input integration, subject to compatibility and licensing review.
3. **Windows compatibility:** experimental Wine and CPU translation integration; application support is not guaranteed.
4. **Virtual machines:** QEMU feasibility research; hardware acceleration must be verified on the target device.

Android does not natively install Windows `.exe` files. Wine is a compatibility layer, not a Windows installation. PRoot is userspace isolation, not a separate Linux kernel or full virtual machine. Pixel 9 Pro XL uses a Mali GPU, so Adreno-specific graphics instructions cannot be assumed to work.

## Build

The GitHub Actions workflow installs the Android SDK and Gradle, builds a debug APK, runs unit tests and lint, and uploads artifacts.

Local build commands (after installing JDK 17, Android SDK and Gradle 8.11.1):

```bash
gradle assembleDebug
gradle testDebugUnitTest
gradle lint
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Compatibility and limitations](docs/COMPATIBILITY.md)
- [Security model](docs/SECURITY.md)
- [Contributing](CONTRIBUTING.md)

## Privacy and licensing

The app uses Android network permissions to download the pinned Debian rootfs and, once enabled, retrieve Debian package indexes and packages over the network. It reads active-network DNS server addresses to configure the private Debian rootfs. It requires no account or cloud runtime, and does not send telemetry. Environment files remain in app-private storage by default. Third-party license review remains a gate before production redistribution.

No repository-wide license has been selected yet. Do not assume this repository grants redistribution rights for third-party components.


## Native runtime status

Development APK CI builds and packages a pinned Android ARM64 PRoot candidate, includes the corresponding source/build archive and license notices, and exposes a Settings diagnostic to test native startup. The Environments screen can install a pinned Debian 12 Bookworm ARM64 rootfs with SHA-256 verification, bounded extraction and ELF architecture validation. The persistent pipe shell, native PTY bridge, scoped ANSI/VT screen renderer, environment manager, APT software manager and streamed TAR.GZ backup/export are implemented. CI builds the PTY bridge from source with the pinned Android NDK; the legacy pipe shell has controlled-fixture tests. Backups include a format manifest and a SHA-256 digest of the complete compressed archive. Real PTY/PRoot-to-Debian execution, DNS resolution and package downloads on the Pixel remain unverified. No graphical desktop or Windows EXE execution is available.


## Linux desktop integration status

Termux:X11 has been evaluated as a possible optional backend. It is not bundled, and no Linux graphical desktop is currently available in PIXEL DESK. The two-app setup, GPL-3.0 licensing, Android app sandbox, PRoot shared-`/tmp` socket and external process lifecycle remain integration gates. See [desktop backend evaluation](docs/DESKTOP_BACKEND_EVALUATION.md).


Settings includes optional Termux / Termux:X11 package detection and a link to the official release page. This is setup diagnostics only; no graphical desktop session can currently be launched by PIXEL DESK.


The software manager can discover installed Linux desktop application entries and display their metadata. GUI launching is intentionally disabled until the external X11 backend and process lifecycle are implemented and validated.
