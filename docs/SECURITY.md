# Security model

- Treat imported archives, packages, scripts and Windows installers as untrusted.
- Never auto-execute an imported installer.
- Show source, file name, size and integrity information before installation.
- Keep environments in app-private storage by default.
- Use Storage Access Framework for user-selected import/export.
- Do not request root or Accessibility Service access for basic operation.
- Do not add network permission until a concrete feature requires it; document each use.
- Verify dependency provenance, licenses and checksums.
- Do not run arbitrary plugin code.
- Provide explicit environment deletion and backup controls.
- Do not claim sandboxing stronger than the actual PRoot/Android boundary.
