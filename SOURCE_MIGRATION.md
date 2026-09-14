# Editable source migration

The repository migration to a normal editable Android/Gradle source tree is complete. The historical `.source/xz*.b64` payload remains untouched only as a recovery snapshot.

This development line deliberately remains at `0.8.0-dev`.

Current editable-source baseline:

- Material 3 foundation and shared edge-to-edge Activity behavior.
- Direct shared-storage permission controller with Android 11+ all-files settings flow.
- Internal Kadb 2.1.4 wireless pairing, mDNS discovery, reconnect, shell, sync listing, and pull support; no Shizuku/LADB runtime dependency.
- Explicit Android security boundary: ADB runs with the device `shell` identity unless the device itself grants more.
- Direct and ADB file browsers with search, name/date/size sorting, hidden-entry filtering, safe FileProvider preview, and guarded root navigation.
- ADB discovery keeps pairing and connection endpoints on the same host and supports reconnecting to previously paired devices without another pairing code.
- File path/name policy rejects traversal and protects critical Android directory roots from direct mutation.
- Confirmed move-to-trash instead of immediate destructive deletion.
- Save Scout package validation and conservative probing of reachable external application locations.
- SQLite Studio working-copy editing with integrity checks before persistence.
- Source sanity checks and unit tests for path, shell escaping, remote path, Save Scout, and SQLite behavior.

## Build source of truth

The active GitHub Actions workflow builds the repository's editable root source directly. It no longer reconstructs `.source` before building. `scripts/source_sanity.py` intentionally rejects a regression back to the archived payload.

The `.source` directory is archival/recovery material only and should not receive normal feature work.
