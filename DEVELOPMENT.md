# OmniFiles Development

The normal Android/Gradle tree in the repository root is the canonical editable source for OmniFiles. The historical `.source/` payload is retained only as a recovery snapshot and must not be used by the active APK build workflow.

Current priorities:

- keep OmniFiles usable as a single APK without requiring Shizuku, LADB, or a PC at runtime;
- use Android Wireless Debugging only through explicit user pairing for shell-level access;
- preserve Android sandbox boundaries and fail clearly when an operation needs privileges Android does not grant;
- keep direct-storage and ADB browsers responsive, searchable, consistently sorted, and safe around protected paths;
- keep ADB endpoint discovery bound to one host and allow reconnecting to already-paired devices without unnecessary re-pairing;
- keep SQLite editing transactional and Save Scout conservative about paths it can actually reach;
- gate builds on source sanity, unit tests, Android Lint, APK archive validation, and checksums.

## Source policy

`app/`, Gradle files, resources, tests, and `scripts/` are the source of truth. New development must be made there. `.source/` is archival only.

## Version policy

Development stays on `0.8.0-dev` until an intentional release decision is made. Feature work and bug fixes do not automatically bump the version.

## CI policy

`.github/workflows/build-apk.yml` must build the editable root project directly. `scripts/source_sanity.py` rejects regressions that attempt to reconstruct the archived `.source` snapshot and also verifies the core test/lint/build gates remain present.
