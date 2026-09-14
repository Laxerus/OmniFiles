# Editable source migration

The historical `.source/xz*.b64` payload remains untouched as a recovery snapshot. The repository root now also contains a normal Android/Gradle source tree so OmniFiles can be reviewed and evolved file-by-file.

This migration deliberately keeps the app version at `0.8.0-dev`.

Initial editable-source priorities implemented here:

- Material 3 + dynamic color foundation and edge-to-edge Activity base.
- Direct shared-storage permission controller with Android 11+ all-files settings flow.
- Internal Kadb 2.1.4 wireless pairing and shell session manager; no Shizuku/LADB runtime dependency.
- Explicit Android security boundary: ADB runs with the device `shell` identity unless the device itself grants more.
- File browser with safe FileProvider opening and confirmed move-to-trash instead of immediate destructive deletion.
- Canonical-path guard and shell-argument escaping primitives with unit tests.

The archived-source GitHub workflow still builds the snapshot until CI migration is completed. The editable tree is the path forward for subsequent commits.
