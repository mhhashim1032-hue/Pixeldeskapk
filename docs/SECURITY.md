# Security model

- Treat imported archives, packages, scripts and Windows installers as untrusted.
- Never auto-execute an imported installer.
- Show source, file name, size and integrity information before installation.
- Keep environments in app-private storage by default.
- Use Storage Access Framework for user-selected import/export.
- Do not request root or Accessibility Service access for basic operation.
- INTERNET permission supports the pinned Debian rootfs download and Debian APT package index/package retrieval. Rootfs downloads require HTTPS and a pinned SHA-256 digest, with strict download and expanded-size limits.
- ACCESS_NETWORK_STATE is used to read DNS server addresses from Android's active network and write a resolver file inside the app-private Debian rootfs; DNS addresses are not uploaded.
- Treat APT packages and maintainer scripts as untrusted code. Use configured Debian repositories, make package operations explicit, and never describe PRoot as a strong security boundary or separate kernel.
- Do not accept arbitrary rootfs URLs or execute downloaded archives.
- Verify dependency provenance, licenses and checksums.
- Keep the PRoot corresponding source and relinkable build inputs with the development APK; production redistribution remains blocked pending license review.
- Do not run arbitrary plugin code.
- Require explicit confirmation before environment deletion; validate canonical containment and never follow symlinks while deleting a workspace.
- Export environments only while stopped; stream to a user-selected SAF destination without requesting broad storage permissions.
- Reject relative symbolic links that escape the workspace and special filesystem nodes during backup; never follow symbolic links while traversing the workspace.
- Include a versioned backup manifest and report a SHA-256 digest of the complete compressed archive.
- Do not claim sandboxing stronger than the actual PRoot/Android boundary.
