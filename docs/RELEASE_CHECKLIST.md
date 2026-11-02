# Release readiness checklist

## Development APK candidate

- [x] Android CI builds a debug APK.
- [x] Unit tests and Android lint run in CI.
- [x] Native PRoot and POSIX PTY libraries are packaged for ARM64.
- [x] Manual installable-candidate workflow verifies package ID and required native libraries.
- [ ] Install the generated APK on a physical Pixel 9 Pro XL and verify launch, storage permissions, Debian download/extraction, PRoot shell, PTY, DNS and APT.
- [ ] Test app upgrade, data retention, backup/export and uninstall behavior.

The candidate workflow uploads an installable **debug-signed** APK. It is for sideload testing only and is not a production-signed or Play Store-ready release.

## Production release

- [ ] Complete physical-device validation and resolve all known compatibility issues.
- [ ] Complete dependency and bundled native payload license inventory.
- [ ] Review privacy, data handling, support and product disclosure copy.
- [ ] Choose and document the repository/product license.
- [ ] Create a dedicated release keystore outside the repository.
- [ ] Configure protected GitHub Actions secrets for keystore bytes, store password, key alias and key password.
- [ ] Add a release signing configuration that fails closed when secrets are absent.
- [ ] Build and verify a signed release APK/AAB; record SHA-256 and provenance.
- [ ] Review native ARM64 packaging and ABI behavior on the target device.
- [ ] Tag the validated source commit and publish release notes with known limitations.

## Current non-release limitations

- PRoot is not a virtual machine or a security boundary against malicious Linux applications.
- Physical Pixel integration testing has not yet been completed by CI.
- Full xterm compatibility is incomplete.
- Linux graphical desktop / X11 launch is not implemented.
- Windows/Wine and QEMU remain research items.
