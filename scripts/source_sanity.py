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
    "app/src/main/java/dev/laxerus/omnifiles/ui/MainActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/FileBrowserActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbPairingActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbBrowserActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/adb/AdbSessionManager.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/FileOperations.kt",
    "app/src/test/java/dev/laxerus/omnifiles/fs/FileOperationsTest.kt",
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

app_gradle = ROOT / "app/build.gradle"
if app_gradle.is_file():
    text = app_gradle.read_text(encoding="utf-8")
    version = re.search(r"versionName\s+['\"]([^'\"]+)['\"]", text)
    if not version or version.group(1) != "0.8.0-dev":
        errors.append("versionName must remain 0.8.0-dev during this development line")
    if not re.search(r"com\.flyfishxu:kadb:[0-9][^'\"\s]*", text):
        errors.append("embedded Kadb dependency is missing")
    if not re.search(r"com\.flyfishxu:kadb-mdns:[0-9][^'\"\s]*", text):
        errors.append("Kadb mDNS dependency is missing")

workflow = ROOT / ".github/workflows/build-apk.yml"
if workflow.is_file():
    text = workflow.read_text(encoding="utf-8")
    if "source.tar.xz" in text or "Reconstruct and verify OmniFiles source" in text:
        errors.append("build workflow must compile the editable repository tree, not the archived .source snapshot")
    if "gradle --no-daemon --stacktrace :app:testDebugUnitTest" not in text:
        errors.append("build workflow must run unit tests")
    if "gradle --no-daemon --stacktrace :app:lintDebug" not in text:
        errors.append("build workflow must run Android Lint")
    if "gradle --no-daemon --stacktrace :app:assembleDebug" not in text:
        errors.append("build workflow must assemble the debug APK")

file_operations = ROOT / "app/src/main/java/dev/laxerus/omnifiles/fs/FileOperations.kt"
if file_operations.is_file():
    text = file_operations.read_text(encoding="utf-8")
    for signature in ("fun copy(", "fun move(", "requireNotInsideSource", "rollbackCreated"):
        if signature not in text:
            errors.append(f"safe transfer primitive missing from FileOperations: {signature}")
    if "overwrite = true" in text:
        errors.append("file transfer must not silently overwrite existing user files")

file_browser = ROOT / "app/src/main/java/dev/laxerus/omnifiles/ui/FileBrowserActivity.kt"
if file_browser.is_file():
    text = file_browser.read_text(encoding="utf-8")
    for token in ("TransferMode.COPY", "TransferMode.MOVE", "pastePendingTransfer", "STATE_CURRENT_PATH"):
        if token not in text:
            errors.append(f"file browser transfer/state wiring missing: {token}")

browser_layout = ROOT / "app/src/main/res/layout/activity_file_browser.xml"
if browser_layout.is_file():
    text = browser_layout.read_text(encoding="utf-8")
    for view_id in ("@+id/pasteButton", "@+id/cancelTransferButton", "@+id/operationProgress"):
        if view_id not in text:
            errors.append(f"file browser transfer control missing: {view_id}")

workflow_render = ROOT / "scripts/render_build_apk.sh"
if workflow_render.is_file():
    text = workflow_render.read_text(encoding="utf-8")
    for task in (":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebug"):
        if task not in text:
            errors.append(f"Render APK pipeline missing gate: {task}")

for path in ROOT.glob("app/src/main/java/**/*.kt"):
    text = path.read_text(encoding="utf-8")
    if "Runtime.getRuntime().exec" in text or "ProcessBuilder(\"su\"" in text:
        errors.append(f"unreviewed direct privilege process launch: {path.relative_to(ROOT)}")
    if "override fun onBackPressed" in text:
        errors.append(f"deprecated onBackPressed override: {path.relative_to(ROOT)}")
    if "deleteRecursively()" in text and path.name != "TrashManager.kt" and "cache" not in text.lower():
        errors.append(f"review recursive delete outside trash/cache layer: {path.relative_to(ROOT)}")

for path in ROOT.glob("app/src/test/java/**/*.kt"):
    text = path.read_text(encoding="utf-8")
    if "createTempDir(" in text:
        errors.append(f"deprecated Kotlin createTempDir in test: {path.relative_to(ROOT)}")
    if "createTempFile(" in text and "kotlin.io.path.createTempFile" not in text:
        errors.append(f"deprecated/unqualified Kotlin createTempFile in test: {path.relative_to(ROOT)}")

if errors:
    print("OmniFiles source sanity: FAILED")
    for item in errors:
        print(f" - {item}")
    sys.exit(1)

print("OmniFiles source sanity: OK")
