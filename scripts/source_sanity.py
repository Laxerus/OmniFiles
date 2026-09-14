#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = [
    "settings.gradle",
    "build.gradle",
    "gradle.properties",
    "app/build.gradle",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/dev/laxerus/omnifiles/access/StorageAccessController.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/FilePathPolicy.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/MainActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/FileBrowserActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbPairingActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbBrowserActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/adb/AdbSessionManager.kt",
    "app/src/main/java/dev/laxerus/omnifiles/scout/SaveScoutService.kt",
    "app/src/main/java/dev/laxerus/omnifiles/sqlite/SqliteWorkspace.kt",
    ".github/workflows/build-apk.yml",
]

errors: list[str] = []

for rel in REQUIRED:
    path = ROOT / rel
    if not path.is_file():
        errors.append(f"missing required file: {rel}")

for path in ROOT.glob("app/src/main/res/**/*.xml"):
    try:
        ET.parse(path)
    except ET.ParseError as exc:
        errors.append(f"invalid XML: {path.relative_to(ROOT)}: {exc}")

manifest = ROOT / "app/src/main/AndroidManifest.xml"
if manifest.is_file():
    text = manifest.read_text(encoding="utf-8")
    if "android:allowBackup=\"false\"" not in text:
        errors.append("AndroidManifest.xml must keep allowBackup=false")
    if "android:usesCleartextTraffic=\"false\"" not in text:
        errors.append("AndroidManifest.xml must keep usesCleartextTraffic=false")
    if "android.permission.MANAGE_EXTERNAL_STORAGE" not in text:
        errors.append("shared-storage manager permission is missing")
    if "android:exported=\"false\"" not in text:
        errors.append("internal Android components must remain non-exported")

app_gradle = ROOT / "app/build.gradle"
if app_gradle.is_file():
    text = app_gradle.read_text(encoding="utf-8")
    version = re.search(r"versionName\s+['\"]([^'\"]+)['\"]", text)
    if not version or version.group(1) != "0.8.0-dev":
        errors.append("versionName must remain 0.8.0-dev during this development line")
    if "compileSdk 36" not in text or "targetSdk 36" not in text:
        errors.append("compileSdk/targetSdk must remain aligned with Android 36")
    if "com.flyfishxu:kadb:2.1.4" not in text:
        errors.append("embedded Kadb dependency is missing")
    if "com.flyfishxu:kadb-mdns:2.1.4" not in text:
        errors.append("Kadb mDNS dependency is missing")

workflow = ROOT / ".github/workflows/build-apk.yml"
if workflow.is_file():
    text = workflow.read_text(encoding="utf-8")
    forbidden_snapshot_markers = ("source.tar.xz", ".source/xz", "Reconstruct and verify OmniFiles source")
    for marker in forbidden_snapshot_markers:
        if marker in text:
            errors.append(f"build workflow regressed to archived source snapshot: {marker}")
    for required_step in (":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebug"):
        if required_step not in text:
            errors.append(f"build workflow is missing required gate: {required_step}")

for path in ROOT.glob("app/src/main/java/**/*.kt"):
    text = path.read_text(encoding="utf-8")
    if "Runtime.getRuntime().exec" in text or "ProcessBuilder(\"su\"" in text:
        errors.append(f"unreviewed direct privilege process launch: {path.relative_to(ROOT)}")
    if "deleteRecursively()" in text and path.name != "TrashManager.kt" and "cache" not in text.lower():
        errors.append(f"review recursive delete outside trash/cache layer: {path.relative_to(ROOT)}")

if errors:
    print("OmniFiles source sanity: FAILED")
    for item in errors:
        print(f" - {item}")
    sys.exit(1)

print("OmniFiles source sanity: OK")
