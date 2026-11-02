# PIXEL DESK security model and QA gates

**Status:** engineering threat model; not a certification or penetration-test report.

## Security objectives

- Keep Linux rootfs, home, downloads and backups within PIXEL DESK's app-private storage unless the user explicitly exports/imports through Android's Storage Access Framework.
- Treat downloaded rootfs archives, desktop-entry files, package metadata and all terminal output as untrusted input.
- Prevent archive path traversal, escaping symlinks, special filesystem nodes, duplicate paths and resource-exhaustion archives.
- Never evaluate a desktop-entry `Exec` field as a shell command.
- Bound native JNI input sizes and avoid unbounded terminal buffers.
- Keep unsupported desktop and Windows capabilities visibly disabled rather than implying isolation or compatibility that has not been proven.

## Assets and trust boundaries

| Asset / boundary | Threat | Current controls |
|---|---|---|
| Debian rootfs archive | Path traversal, symlink escape, hard-link abuse, decompression bomb | Staged extraction, canonical path checks, link validation, entry/expanded/per-file limits, special-node rejection |
| Workspace files | Symlink/path escape through file picker or UI | Canonical workspace-relative path validation; SAF imports are copied into controlled downloads directory |
| Backup export | Symlink traversal, huge archive, environment mutating during export | Environment stopped lock, symlink-safe traversal, entry/size caps, compressed archive SHA-256 |
| APT package names | Shell injection through user input | Strict Debian package-name allowlist; package commands use only parsed names |
| Desktop-entry metadata | Malicious `Exec` string or shell metacharacters | Parse metadata only; safe token preflight rejects unsafe tokens; only quoted `command -v` checks run |
| Native PTY JNI | Oversized arrays, invalid executable path, malformed environment variables, integer overflow | Absolute executable path requirement, bounded argument/environment counts and sizes, environment-key validation, safe byte-array bounds |
| External Termux:X11 | Cross-app private storage/socket assumptions, untrusted loader APIs, license obligations | Detection only; no silent install, no hardcoded signature, no launch path until socket and lifecycle are validated |

## Isolation limits

PRoot is a userspace filesystem/process compatibility layer. It is **not** a virtual machine, kernel boundary, Android sandbox replacement or security boundary against malicious Linux code. Linux processes still run under the PIXEL DESK Android app UID and share the Android kernel. APT maintainer scripts and applications execute with the permissions available to the app process, subject to Android's sandbox and SELinux policy.

Do not store secrets in the Linux environment that should not be accessible to code installed inside it. Do not claim that PRoot makes hostile packages safe.

## Native PTY controls

- Only absolute native runtime executable paths are accepted by JNI.
- Terminal dimensions are capped at 300 rows by 500 columns.
- JNI argument arrays are capped at 256 entries; environment arrays at 128 entries; each string is bounded.
- Environment keys must use ASCII letters, digits and underscore, with a valid first character.
- Native read/write calls cap transfers at 1 MiB and validate offsets without signed addition overflow.
- PTY child lifecycle is owned by the session; close shuts the master descriptor and terminates/reaps the child process group.

## Required release gates

- Run Android CI build, unit tests and lint on every PR.
- Inspect the native payload and every transitive dependency license; ship corresponding source/build inputs where required.
- Verify rootfs download, extraction, DNS, APT and PTY behavior on a physical ARM64 device.
- Test cancellation, app process death, orientation/resize and repeated start/stop for leaked processes or descriptors.
- Verify SAF export/import with large files and interrupted/cancelled operations.
- Test malformed archives, hostile desktop-entry metadata and oversized JNI arguments.
- Complete an external desktop backend review before enabling GUI app launch.
- Configure a signed release key outside the repository; never commit signing secrets.

## Known unverified items

- Physical Pixel 9 Pro XL PRoot and native PTY validation has not been completed by CI.
- Full xterm compatibility is not implemented.
- Termux:X11 socket sharing and desktop session lifecycle are not implemented.
- No security penetration test has been performed.

*This document describes implemented safeguards and remaining validation work; it does not certify the application as secure.*
