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

The app downloads, verifies and extracts a pinned Debian 12 Bookworm ARM64 rootfs into app-private storage, packages a native PRoot candidate, and provides a bounded command runner plus a persistent non-PTY shell adapter. The terminal adapter preserves shell state between commands and bounds output and command duration. CI tests use a controlled shell fixture; real PRoot execution against the Debian rootfs on a physical Pixel remains a required validation gate. No graphical desktop, Windows EXE execution or VM is implemented.

## Design constraints

- No root dependency for the initial Linux userspace target.
- No cloud execution or mandatory account.
- No arbitrary downloaded plugin code.
- Avoid broad storage permissions; use Android's Storage Access Framework for user-selected files.
- Keep runtime operations cancellable and observable.

## Workspace storage and file operations

- `WorkspaceStorage` creates app-private per-environment directories for a root filesystem, home directory, downloads and temporary files, and deletes selected workspaces without following symbolic links.
- `EnvironmentRuntimeManager` owns one persistent PRoot shell session per environment, lets the Terminal and Apps screens borrow the same session, tracks the selected environment, and stops sessions when the activity is destroyed.
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

It is used by the Debian rootfs installer. The catalog pins source URL, version, SHA-256, download size limit, expanded-size limit and architecture. Downloaded archives are never executed.

## Linux runtime integration decision

The first target remains an ARM64 Debian userspace under PRoot on unrooted Android. PRoot-Distro is useful as a technical reference for rootless OCI/rootfs management, but it is a separate GPL-3.0 project and is not copied or bundled by this repository. PRoot itself and its Android-specific dependencies require a reproducible Android/NDK build and license review. A Termux binary cannot simply be assumed executable from another app's private sandbox.

Before the runtime can be declared validated on the target device, the project must:
1. verify the native PRoot startup probe on the target Pixel;
2. launch a minimal non-interactive command inside the installed rootfs and collect exit code/stdout/stderr;
3. test process cancellation, app backgrounding, storage exhaustion and cleanup on a physical ARM64 device;
4. complete the dependency/license review for production redistribution.

References:
- PRoot-Distro project and architecture: https://github.com/termux/proot-distro
- Termux PRoot package build metadata and dependencies: https://github.com/termux/termux-packages/tree/master/packages/proot


## Safe rootfs archive extraction

`SafeRootfsExtractor` supports gzip-compressed TAR archives and extracts into a fresh staging directory before promotion. It enforces configurable entry-count, total expanded-size and per-file limits; rejects absolute archive paths, parent traversal, duplicate file paths and special filesystem nodes; materializes hard links as bounded copies; and validates symbolic-link targets remain inside the rootfs. Absolute Linux symlinks are rewritten as equivalent relative links rooted inside the extraction directory, so common paths such as `/bin` do not become host-absolute Android paths. File ownership and privileged mode bits are not restored.

Device nodes remain deliberately rejected. Hard-link targets must refer to an earlier regular file inside the rootfs and are copied with size and cancellation checks, avoiding host inode aliasing. The extractor is connected to the Debian installer; physical-device archive validation remains outstanding.


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


## Debian rootfs installation

`RootfsCatalog` pins the Debian 12 Bookworm ARM64 OCI rootfs layer from a fixed `debuerreotype/docker-debian-artifacts` commit. `RootfsInstaller` downloads it through `VerifiedDownloader`, checks the pinned SHA-256, extracts it with bounded TAR.GZ handling, and validates `/etc/os-release`, `usr/bin/dpkg` and the ELF64 AArch64 machine type of a POSIX shell before reporting the rootfs installed.

The installer runs on `Dispatchers.IO`, refuses to overwrite a non-empty rootfs, removes partial installations on failure/cancellation, and deletes the downloaded archive after installation. Shell execution is a separate runtime operation and still requires physical-device validation.


## Persistent shell adapter

`ProotInteractiveSession` starts the validated guest shell through the native PRoot executable and keeps one process alive for sequential commands. It serializes commands, appends a unique exit-status marker, captures merged output with a fixed retained-output ceiling, and closes the session on timeout or cancellation. The adapter uses pipes rather than a PTY: terminal control sequences, full-screen applications, job control and true SIGINT delivery are not supported yet.

The runtime manager owns the session lifecycle; screens borrow rather than destroy the session when navigation changes. The Terminal screen records bounded recent-command history and exposes reconnect and stop controls. Sessions are closed when explicitly stopped or when the Android activity is destroyed.

## Network and package management

Android's `INTERNET` permission is used for rootfs and package downloads. `ACCESS_NETWORK_STATE` allows the app to read DNS servers from the active Android network and generate `/etc/resolv.conf` inside the app-private Debian rootfs. DNS configuration does not upload those addresses.

APT operations are built from a strict allowlist-style package-name validator; user-entered shell fragments are never interpolated into package commands. Package installation still executes Debian package maintainer scripts in the PRoot environment, so packages and repositories remain untrusted inputs and PRoot must not be described as a hardened sandbox.


## Environment lifecycle

`WorkspaceStorage` manages named app-private workspace directories. Deletion validates the canonical parent and environment identifier, then uses `Files.walkFileTree` without following symbolic links. The UI requires explicit confirmation before deleting the rootfs, installed packages, home files and downloads.

`EnvironmentRuntimeManager` keeps a single persistent shell process per environment, selects the most recently started environment for Terminal and Apps, and coordinates start/stop operations. The Activity stops all managed processes on destruction; the manager rejects new starts during shutdown and resumes when the Activity is recreated.


## Environment backup and export

`EnvironmentBackupExporter` streams the selected app-private workspace to a user-chosen Storage Access Framework destination as a TAR.GZ archive. The archive contains the complete workspace tree and a small format/version manifest. Regular files are streamed with bounded entry-count and expanded-size limits; directories and symbolic links are represented as TAR entries without following links. Relative symbolic links that resolve outside the workspace and special filesystem nodes are rejected. The SHA-256 digest covers the final compressed archive bytes. Export is disabled while that environment's PRoot session is running so the backup does not capture an actively changing rootfs.


## PTY-backed Linux terminal

`ProotPtySession` uses the ARM64 JNI bridge in `app/src/main/cpp/pty_bridge.cpp` to allocate a POSIX master/slave terminal pair, create a controlling session for the PRoot child and connect all three standard streams to the slave. The bridge exposes bounded window resizing, byte-oriented reads/writes and process-group signals. The native library is compiled for Android API 28+ by `scripts/prepare-proot-native.sh` and packaged as `libpixeldeskpty.so`.

The runtime manager owns PTY and legacy pipe sessions through a shared lifecycle contract, so stop, Activity shutdown and backup exclusion apply to both. The terminal screen sends input through the PTY and exposes Ctrl+C. A VT/ANSI renderer is not included in this phase: full-screen programs that depend on cursor addressing, alternate screens or rich terminal attributes remain unsupported until that renderer is implemented and tested. The JNI process-spawn path and real PRoot interaction still require physical Pixel validation.


## ANSI/VT terminal screen model

The terminal UI consumes PTY bytes through a bounded in-memory screen model. It incrementally decodes UTF-8 across read boundaries and interprets common control characters, CSI cursor movement, erase/insert/delete/scroll commands, scroll regions, cursor visibility, alternate-screen switching and ANSI 16/256/true-color SGR attributes. Rendering is a Compose monospace cell grid; terminal input includes Enter, Tab, Escape and arrow-key sequences.

This is a deliberately scoped VT subset, not a full xterm implementation. OSC hyperlinks, mouse reporting, bracketed paste, advanced keyboard protocols, Unicode grapheme width and every terminal mode are not implemented. Application compatibility must be validated individually on a physical device.


## ANSI/VT terminal screen model

The terminal UI consumes PTY bytes through a bounded in-memory screen model. It incrementally decodes UTF-8 across read boundaries and interprets common control characters, CSI cursor movement, erase/insert/delete/scroll commands, scroll regions, cursor visibility, alternate-screen switching and ANSI 16/256/true-color SGR attributes. Rendering is a Compose monospace cell grid; terminal input includes Enter, Tab, Escape and arrow-key sequences.

This is a deliberately scoped VT subset, not a full xterm implementation. OSC metadata is ignored; OSC hyperlinks, mouse reporting, bracketed paste, advanced keyboard protocols, Unicode grapheme width and every terminal mode are not implemented. Application compatibility must be validated individually on a physical device.


## Linux graphical desktop decision

The initial backend review is recorded in `docs/DESKTOP_BACKEND_EVALUATION.md`. Termux:X11 is a plausible optional external X server, but it requires both its Android app and companion Termux package. Its documented PRoot workflow depends on a shared temporary/socket path; PIXEL DESK's current app-private `/tmp` is not shared with Termux. The upstream project is GPL-3.0, so bundling or redistribution is blocked until the product license and corresponding-source obligations are reviewed. The next step is a sandbox-safe external-backend prototype, not embedding a copied APK or hardcoding the upstream loader signature.


## External desktop package diagnostics

Settings can detect the separately installed Android packages `com.termux` and `com.termux.x11` through Android package visibility queries. This is informational only: detecting both APKs does not verify the Termux companion package, a usable X11 socket, a signature-checked server launch path, or PRoot access to that socket. The app therefore does not launch a desktop session from this screen. Setup links open the official upstream release page only after an explicit user tap.


The Settings diagnostic intentionally checks Android package visibility only. It does not claim that Termux's companion command is installed, that the X11 socket is reachable, or that a desktop session can be launched.
