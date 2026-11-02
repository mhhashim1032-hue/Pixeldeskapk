# PIXEL DESK

**A native Android desktop workspace for running compatible Linux and Windows software on Android.**

PIXEL DESK targets ARM64 Android devices, with the Google Pixel 9 Pro XL as the primary optimization and validation target.

## Current status

The repository now contains the native Kotlin/Jetpack Compose application shell, device information cards, project documentation, unit-test starter, and GitHub Actions build workflows.

**The current app is a foundation build.** It does not yet install Linux distributions, launch desktop sessions, run Windows executables, or create virtual machines. Those capabilities are roadmap items and will be marked complete only after implementation and testing.

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

The foundation app has no network permission and requires no account or cloud runtime. Environment files are intended to remain on-device by default. Third-party components will be reviewed for license and redistribution requirements before bundling.

No repository-wide license has been selected yet. Do not assume this repository grants redistribution rights for third-party components.
