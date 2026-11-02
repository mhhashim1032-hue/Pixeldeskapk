# Security model

- Treat imported archives, packages, scripts and Windows installers as untrusted.
- Never auto-execute an imported installer.
- Show source, file name, size and integrity information before installation.
- Keep environments in app-private storage by default.
- Use Storage Access Framework for user-selected import/export.
- Do not request root or Accessibility Service access for basic operation.
- INTERNET permission is used only for the pinned Debian rootfs download; require HTTPS and a pinned SHA-256 digest, with strict download and expanded-size limits.
- Do not accept arbitrary rootfs URLs or execute downloaded archives.
- Verify dependency provenance, licenses and checksums.
- Keep the PRoot corresponding source and relinkable build inputs with the development APK; production redistribution remains blocked pending license review.
- Do not run arbitrary plugin code.
- Provide explicit environment deletion and backup controls.
- Do not claim sandboxing stronger than the actual PRoot/Android boundary.
