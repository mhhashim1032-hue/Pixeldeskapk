# PIXEL DESK

Native Android desktop workspace for running compatible Linux and Windows software on ARM64 Android devices.

## Status
Foundation stage. Linux ARM64 userspace is the first implementation target. Windows compatibility and full virtual machines remain experimental until tested on the Google Pixel 9 Pro XL.

## Principles
- Kotlin and Jetpack Compose native Android app.
- Offline-first; no mandatory accounts, cloud runtime, paid APIs, or telemetry.
- No root requirement for the basic Linux environment.
- Explicit user consent for filesystem access, imported installers, and networking.
- Verify third-party licenses before bundling or redistributing dependencies.
- Never claim compatibility without testing on the target device.

## Planned environments
1. Linux ARM64 userspace and graphical desktop.
2. Experimental Windows compatibility through Wine and suitable CPU/graphics translation.
3. Research track for QEMU virtual machines.

Android does not natively install Windows EXE files. Compatibility depends on CPU architecture, graphics support, system libraries, and each application's requirements. PRoot is userspace isolation, not full virtualization.

## Documentation
Architecture, roadmap, compatibility, and security documentation will be added as the project foundation is implemented.

## License
No repository-wide license has been selected. Third-party dependencies retain their own licenses.