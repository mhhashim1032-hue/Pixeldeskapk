# PIXEL DESK

**A native Android desktop workspace for running compatible Linux and Windows software on Android.**

PIXEL DESK targets ARM64 Android devices, with the Google Pixel 9 Pro XL as the primary optimization and validation target.

## Current status

The repository now contains a native Kotlin/Jetpack Compose app, a pinned Debian 12 Bookworm ARM64 rootfs installer, a bundled Android ARM64 PRoot candidate, runtime diagnostics, secure archive extraction and GitHub Actions build workflows.

**Current limitation:** the app can download, verify, extract and validate the Debian rootfs, and can probe PRoot startup. It has not yet launched a guest shell on a physical Pixel. Desktop sessions, Windows executables and virtual machines are not working features yet.

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

The app uses Android's INTERNET permission only to download the pinned Debian rootfs over HTTPS and verify its SHA-256 digest. It requires no account or cloud runtime, and does not send telemetry. Environment files remain in app-private storage by default. Third-party license review remains a gate before production redistribution.

No repository-wide license has been selected yet. Do not assume this repository grants redistribution rights for third-party components.


## Native runtime status

Development APK CI builds and packages a pinned Android ARM64 PRoot candidate, includes the corresponding source/build archive and license notices, and exposes a Settings diagnostic to test native startup. The Environments screen can install a pinned Debian 12 Bookworm ARM64 rootfs with SHA-256 verification, bounded extraction and ELF architecture validation. The guest shell has not yet been launched on-device; no desktop session or Windows EXE execution is available.
