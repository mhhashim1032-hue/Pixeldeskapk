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
