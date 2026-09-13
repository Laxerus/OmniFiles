# OmniFiles Development

The current repository stores the Android project payload in `.source/xz00.b64` through `.source/xz06.b64`. The verified build workflow reconstructs that payload before running sanity checks, unit tests, Android Lint, and `assembleDebug`.

Current priorities:

- keep OmniFiles usable as a single APK without requiring Shizuku, LADB, or a PC at runtime;
- use Android Wireless Debugging only through explicit user pairing for shell-level access;
- preserve Android sandbox boundaries and fail clearly when an operation needs privileges Android does not grant;
- keep the Material 3 interface responsive, modern, and safe for destructive file operations;
- gate releases on source sanity, tests, lint, APK archive validation, and checksums.

The `.source` payload remains the canonical source until the project tree is migrated to normal repository files.
