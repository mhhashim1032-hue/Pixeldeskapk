# Architecture

## Application layers

- **UI:** Jetpack Compose screens and navigation.
- **Domain:** environment lifecycle contracts and use cases.
- **Data:** local metadata, configuration and operation logs.
- **Runtime adapters:** isolated integration points for Linux userspace, graphical sessions and future compatibility engines.

## Planned runtime boundaries

1. Android host app owns UI, permissions, lifecycle and local metadata.
2. Linux environment data is stored in app-private storage by default.
3. Runtime processes are launched through an explicit environment controller.
4. Imported installers and archives are treated as untrusted input.
5. Windows compatibility and VM implementations remain optional adapters.

## Initial implementation

The current foundation is a native Compose shell. It does not yet install Linux, launch a desktop session, execute EXE files, or create virtual machines. The UI labels these capabilities as planned, experimental or research.

## Design constraints

- No root dependency for the initial Linux userspace target.
- No cloud execution or mandatory account.
- No arbitrary downloaded plugin code.
- Avoid broad storage permissions; use Android's Storage Access Framework for user-selected files.
- Keep runtime operations cancellable and observable.

## Workspace storage and file operations

- `WorkspaceStorage` creates app-private per-environment directories for a future root filesystem, home directory, downloads and temporary files.
- `WorkspaceFiles` provides a deliberately small file API for listing, creating directories, creating text files, previewing small text files and deleting files or empty directories.
- Every path is canonicalized and checked against the selected workspace root. Symbolic links that resolve outside the root are excluded from listings and rejected for reads/writes.
- The Library screen is currently a workspace file browser. It is not a Linux shell and does not expose a mounted Linux root filesystem.

## Verified archive downloads

`VerifiedDownloader` is a transport primitive for a future environment installer. It:
- accepts HTTPS descriptors only;
- requires a pinned 64-character SHA-256 digest and maximum byte count;
- checks the final URL after redirects remains HTTPS;
- streams to a temporary `.part` file, checks cancellation and size limits, and verifies the digest;
- promotes the archive only after successful verification and removes partial data on failure.

It does not yet extract a root filesystem, install a runtime executable, or execute downloaded code. A production catalog must pin source URLs, versions, hashes, license metadata and supported CPU architectures before the UI exposes an install action.

## Linux runtime integration decision

The first target remains an ARM64 Debian userspace under PRoot on unrooted Android. PRoot-Distro is useful as a technical reference for rootless OCI/rootfs management, but it is a separate GPL-3.0 project and is not copied or bundled by this repository. PRoot itself and its Android-specific dependencies require a reproducible Android/NDK build and license review. A Termux binary cannot simply be assumed executable from another app's private sandbox.

Before a real shell is enabled, the project must:
1. build or obtain an Android-compatible ARM64 PRoot executable with reproducible provenance;
2. verify all runtime and rootfs artifacts by pinned hashes;
3. extract archives with path traversal, symlink, hardlink, device-node and size protections;
4. validate the rootfs architecture and required shell binaries;
5. launch a minimal non-interactive command and collect exit code/stdout/stderr;
6. test process cancellation, app backgrounding, storage exhaustion and cleanup on a physical ARM64 device.

References:
- PRoot-Distro project and architecture: https://github.com/termux/proot-distro
- Termux PRoot package build metadata and dependencies: https://github.com/termux/termux-packages/tree/master/packages/proot


## Safe rootfs archive extraction

`SafeRootfsExtractor` supports gzip-compressed TAR archives and extracts into a fresh staging directory before promotion. It enforces configurable entry-count, total expanded-size and per-file limits; rejects absolute archive paths, parent traversal, duplicate paths, hard links and special filesystem nodes; and validates symbolic-link targets remain inside the rootfs. Absolute Linux symlinks are rewritten as equivalent relative links rooted inside the extraction directory, so common paths such as `/bin` do not become host-absolute Android paths. File ownership and privileged mode bits are not restored.

The initial implementation deliberately rejects hard links and device nodes. This may exclude some distribution archives; the installer must select and test a compatible rootfs artifact rather than silently weakening these checks. Extraction is not yet connected to the UI or a distribution catalog.


## Native PRoot packaging investigation

The next runtime adapter will be an Android-native ARM64 PRoot build, not a Linux/glibc executable. Termux documents that its Android-native programs use Bionic and the Android NDK; its PRoot package build recipe also identifies Android-specific shared-memory support and Talloc as dependencies.

For Android app packaging, the executable and its companion loader need to be placed in a location from which the app can execute native code. The current candidate is an ABI-specific `jniLibs/arm64-v8a` payload, extracted by Android into `ApplicationInfo.nativeLibraryDir`, with packaging configured to extract native libraries as real files. The executable may need an Android-compatible `lib*.so` filename even though it is launched as a process. This must be proven in an instrumentation/device test before bundling.

The PRoot process adapter must set an explicit loader path (rather than relying on Termux's private prefix), use only app-private rootfs/home/tmp paths, invoke PRoot with link-to-symlink handling for guest hard-link operations, and capture stdout, stderr, exit status and cancellation. No environment is considered installed until a minimal ARM64 command succeeds on a physical device.

The static Android PRoot build in `berilevi/proot-static` is a research candidate, not yet an approved dependency. Its source revision, bundled Talloc licensing, static-link redistribution obligations, build reproducibility and runtime behaviour must be reviewed before its output is shipped. Prefer a reproducible build from maintained upstream source where practical.

References:
- Termux execution model and Bionic/NDK details: https://github.com/termux/termux-packages/wiki/Termux-execution-environment
- Termux PRoot package recipe: https://github.com/termux/termux-packages/blob/master/packages/proot/build.sh
- PRoot static build research candidate: https://github.com/berilevi/proot-static


## Native PRoot runtime probe

`scripts/prepare-proot-native.sh` builds a pinned Android ARM64 PRoot candidate with NDK 28.2.13676358, places the executable and companion loaders under `jniLibs/arm64-v8a/`, and embeds the corresponding source/build inputs plus license notices in the APK assets. The Gradle packaging configuration forces native payload extraction so the app can locate the executable in `ApplicationInfo.nativeLibraryDir`.

`ProotRuntimeProbe` checks the ARM64 ABI, verifies the executable and loader files exist, then starts PRoot with the explicit `PROOT_LOADER` path and its version flag. The command runs off the UI thread with a timeout and bounded output capture. This verifies only native process startup; it does not prove rootfs installation, shell execution, graphics, Windows compatibility or physical-device support.

The APK build pipeline and PRoot source archive must remain paired for redistribution. The source archive includes the pinned source tree and build intermediates needed for the static LGPL-linked payload review. Physical Pixel validation remains mandatory before enabling environment installation.
