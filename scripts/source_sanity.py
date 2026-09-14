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
    "app/src/main/java/dev/laxerus/omnifiles/ui/TrashActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/TrashListAdapter.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbPairingActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbBrowserActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbFileListAdapter.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/ChecksumActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/adb/AdbSessionManager.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/DigestUtils.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/FileOperations.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/FileInspector.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/FavoriteStore.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/TrashManager.kt",
    "app/src/main/res/layout/activity_checksum.xml",
    "app/src/main/res/layout/activity_trash.xml",
    "app/src/test/java/dev/laxerus/omnifiles/fs/DigestUtilsTest.kt",
    "app/src/test/java/dev/laxerus/omnifiles/fs/FileOperationsTest.kt",
    "app/src/test/java/dev/laxerus/omnifiles/fs/FileInspectorTest.kt",
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
    if '.ui.TrashActivity' not in text:
        errors.append("TrashActivity must remain registered")
    if '.ui.ChecksumActivity' not in text:
        errors.append("ChecksumActivity must remain registered")

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
    for signature in (
        "fun copy(",
        "fun move(",
        "fun estimateTransferBytes(",
        "requireNotInsideSource",
        "requireEnoughFreeSpace",
        ".usableSpace",
        "MIN_FREE_SPACE_RESERVE_BYTES",
        "rollbackCreated",
        "STAGING_PREFIX",
        "copyFileVerified",
        "MessageDigest.getInstance(\"SHA-256\")",
        "commitStagingCopy",
    ):
        if signature not in text:
            errors.append(f"safe transfer primitive missing from FileOperations: {signature}")
    if "overwrite = true" in text:
        errors.append("file transfer must not silently overwrite existing user files")

file_path_policy = ROOT / "app/src/main/java/dev/laxerus/omnifiles/fs/FilePathPolicy.kt"
if file_path_policy.is_file():
    text = file_path_policy.read_text(encoding="utf-8")
    if "Character.isISOControl" not in text:
        errors.append("file names must reject ambiguous control characters")

digest_utils = ROOT / "app/src/main/java/dev/laxerus/omnifiles/fs/DigestUtils.kt"
if digest_utils.is_file():
    text = digest_utils.read_text(encoding="utf-8")
    for token in ("SHA-256", "sha256Hex", "BUFFER_BYTES", "toHex"):
        if token not in text:
            errors.append(f"checksum primitive missing: {token}")

file_inspector = ROOT / "app/src/main/java/dev/laxerus/omnifiles/fs/FileInspector.kt"
if file_inspector.is_file():
    text = file_inspector.read_text(encoding="utf-8")
    for token in ("DEFAULT_MAX_ENTRIES", "FilePathPolicy.requireInside", "visitedDirectories", "saturatingAdd"):
        if token not in text:
            errors.append(f"bounded file inspection guard missing: {token}")

favorite_store = ROOT / "app/src/main/java/dev/laxerus/omnifiles/fs/FavoriteStore.kt"
if favorite_store.is_file():
    text = favorite_store.read_text(encoding="utf-8")
    for token in ("FilePathPolicy.requireInside", "SharedPreferences", "fun list(", "fun toggle("):
        if token not in text:
            errors.append(f"favorite path safety/persistence missing: {token}")

file_browser = ROOT / "app/src/main/java/dev/laxerus/omnifiles/ui/FileBrowserActivity.kt"
if file_browser.is_file():
    text = file_browser.read_text(encoding="utf-8")
    for token in (
        "TransferMode.COPY",
        "TransferMode.MOVE",
        "sourcePaths: List<String>",
        "pastePendingTransfer",
        "selectedPaths",
        "selectAllVisible",
        "shareSelectedFiles",
        "moveSelectedToTrash",
        "restoreTrashTickets",
        "FileInspector.inspect",
        "FavoriteStore",
        "showFavoritePicker",
        "toggleCurrentFavorite",
        "STATE_CURRENT_PATH",
        "restoreTrash",
    ):
        if token not in text:
            errors.append(f"file browser transfer/selection/detail/favorite wiring missing: {token}")
    if "FileListAdapter(::handleEntryClick, ::handleEntryLongClick, ::showEntryActions)" not in text:
        errors.append("file browser must keep long-press selection separate from the more-actions menu")

browser_layout = ROOT / "app/src/main/res/layout/activity_file_browser.xml"
if browser_layout.is_file():
    text = browser_layout.read_text(encoding="utf-8")
    for view_id in (
        "@+id/favoriteToggleButton",
        "@+id/favoritesButton",
        "@+id/selectionBar",
        "@+id/selectAllButton",
        "@+id/selectionShareButton",
        "@+id/selectionCopyButton",
        "@+id/selectionMoveButton",
        "@+id/selectionTrashButton",
        "@+id/pasteButton",
        "@+id/cancelTransferButton",
        "@+id/operationProgress",
    ):
        if view_id not in text:
            errors.append(f"file browser selection/favorite/transfer control missing: {view_id}")

adb_browser = ROOT / "app/src/main/java/dev/laxerus/omnifiles/ui/AdbBrowserActivity.kt"
if adb_browser.is_file():
    text = adb_browser.read_text(encoding="utf-8")
    for token in (
        "AdbFileListAdapter(::openEntry, ::handleLongPress, ::showEntryActions)",
        "showEntryDetails",
        "copyRemotePath",
        "RemotePathPolicy.normalizeAbsolute(entry.path)",
        "ClipData.newUri",
        "R.string.adb_mode",
    ):
        if token not in text:
            errors.append(f"ADB browser action/detail safety wiring missing: {token}")

adb_manager = ROOT / "app/src/main/java/dev/laxerus/omnifiles/adb/AdbSessionManager.kt"
if adb_manager.is_file():
    text = adb_manager.read_text(encoding="utf-8")
    for token in ("suspend fun healthCheck", "snapshotRemote", "before == after", "destination.length() == after.size"):
        if token not in text:
            errors.append(f"ADB health/pull verification missing: {token}")

adb_adapter = ROOT / "app/src/main/java/dev/laxerus/omnifiles/ui/AdbFileListAdapter.kt"
if adb_adapter.is_file():
    text = adb_adapter.read_text(encoding="utf-8")
    for token in ("private val onMore", "binding.moreButton.setOnClickListener { onMore(entry) }"):
        if token not in text:
            errors.append(f"ADB row more-actions callback missing: {token}")

checksum_activity = ROOT / "app/src/main/java/dev/laxerus/omnifiles/ui/ChecksumActivity.kt"
if checksum_activity.is_file():
    text = checksum_activity.read_text(encoding="utf-8")
    for token in ("ActivityResultContracts.OpenDocument", "DigestUtils::sha256Hex", "ClipboardManager", "STATE_HASH"):
        if token not in text:
            errors.append(f"checksum tool wiring missing: {token}")

trash_manager = ROOT / "app/src/main/java/dev/laxerus/omnifiles/fs/TrashManager.kt"
if trash_manager.is_file():
    text = trash_manager.read_text(encoding="utf-8")
    for token in ("METADATA_DIR", "fun listEntries()", "fun restore(entry:", "fun deletePermanently(", "fun emptyTrash()"):
        if token not in text:
            errors.append(f"persistent trash primitive missing: {token}")
    if "overwrite = true" in text:
        errors.append("trash restore must not silently overwrite existing user files")

trash_activity = ROOT / "app/src/main/java/dev/laxerus/omnifiles/ui/TrashActivity.kt"
if trash_activity.is_file():
    text = trash_activity.read_text(encoding="utf-8")
    for token in ("TrashManager", "restore(entry)", "deletePermanently(entry)", "emptyTrash()"):
        if token not in text:
            errors.append(f"trash center action missing: {token}")

main_activity = ROOT / "app/src/main/java/dev/laxerus/omnifiles/ui/MainActivity.kt"
if main_activity.is_file():
    text = main_activity.read_text(encoding="utf-8")
    for token in (
        "StatFs",
        "renderStorageUsage",
        "storageUsageProgress",
        "storage_access_ready",
        "ChecksumActivity::class.java",
        "verifyAdbHealth",
        ".healthCheck()",
    ):
        if token not in text:
            errors.append(f"home storage/ADB/checksum wiring missing: {token}")

main_layout = ROOT / "app/src/main/res/layout/activity_main.xml"
if main_layout.is_file():
    text = main_layout.read_text(encoding="utf-8")
    for view_id in ("@+id/trashButton", "@+id/checksumButton", "@+id/storageUsageText", "@+id/storageUsageProgress"):
        if view_id not in text:
            errors.append(f"home screen control missing: {view_id}")

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
    if ".putIfAbsent(" in text:
        errors.append(f"API 24 putIfAbsent call would break minSdk 23: {path.relative_to(ROOT)}")

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
