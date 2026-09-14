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
    "app/src/main/java/dev/laxerus/omnifiles/ui/OmniActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/FileBrowserActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/TrashActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/TrashListAdapter.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbPairingActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbBrowserActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbFileListAdapter.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/ChecksumActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/ui/StorageAnalyzerActivity.kt",
    "app/src/main/java/dev/laxerus/omnifiles/adb/AdbSessionManager.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/BrowserStartPathPolicy.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/DigestUtils.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/FileOperations.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/FileInspector.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/FavoriteStore.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/TrashManager.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/StorageAnalyzer.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/TransferRuntime.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/TransferBatchRuntime.kt",
    "app/src/main/java/dev/laxerus/omnifiles/fs/TransferBatchRunner.kt",
    "app/src/main/res/layout/activity_checksum.xml",
    "app/src/main/res/layout/activity_storage_analyzer.xml",
    "app/src/main/res/layout/item_storage_analysis.xml",
    "app/src/main/res/layout/item_storage_category.xml",
    "app/src/main/res/layout/activity_trash.xml",
    "app/src/main/res/layout/dialog_transfer_progress.xml",
    "app/src/main/res/values/transfer_strings.xml",
    "app/src/main/res/values/strings_storage_analyzer.xml",
    "app/src/test/java/dev/laxerus/omnifiles/fs/BrowserStartPathPolicyTest.kt",
    "app/src/test/java/dev/laxerus/omnifiles/fs/DigestUtilsTest.kt",
    "app/src/test/java/dev/laxerus/omnifiles/fs/FileOperationsTest.kt",
    "app/src/test/java/dev/laxerus/omnifiles/fs/FileInspectorTest.kt",
    "app/src/test/java/dev/laxerus/omnifiles/fs/StorageAnalyzerTest.kt",
    "app/src/test/java/dev/laxerus/omnifiles/fs/TransferRuntimeTest.kt",
    "app/src/test/java/dev/laxerus/omnifiles/fs/TransferBatchRunnerTest.kt",
]

errors: list[str] = []


def require_tokens(path: str, tokens: tuple[str, ...], label: str) -> None:
    file = ROOT / path
    if not file.is_file():
        return
    text = file.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            errors.append(f"{label} missing: {token}")


for rel in REQUIRED:
    if not (ROOT / rel).is_file():
        errors.append(f"missing required file: {rel}")

for path in ROOT.glob("app/src/main/res/**/*.xml"):
    try:
        ET.parse(path)
    except ET.ParseError as exc:
        errors.append(f"invalid XML: {path.relative_to(ROOT)}: {exc}")

manifest = ROOT / "app/src/main/AndroidManifest.xml"
if manifest.is_file():
    text = manifest.read_text(encoding="utf-8")
    for token, message in (
        ('android:allowBackup="false"', "AndroidManifest.xml must keep allowBackup=false"),
        ('android:usesCleartextTraffic="false"', "AndroidManifest.xml must keep usesCleartextTraffic=false"),
        ('.ui.TrashActivity', "TrashActivity must remain registered"),
        ('.ui.ChecksumActivity', "ChecksumActivity must remain registered"),
        ('.ui.StorageAnalyzerActivity', "StorageAnalyzerActivity must remain registered"),
    ):
        if token not in text:
            errors.append(message)

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
    for task in (
        "gradle --no-daemon --stacktrace :app:testDebugUnitTest",
        "gradle --no-daemon --stacktrace :app:lintDebug",
        "gradle --no-daemon --stacktrace :app:assembleDebug",
    ):
        if task not in text:
            errors.append(f"build workflow missing gate: {task}")

require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/fs/FileOperations.kt",
    (
        "fun copy(", "fun move(", "fun estimateTransferBytes(", "requireNotInsideSource",
        "requireEnoughFreeSpace", ".usableSpace", "MIN_FREE_SPACE_RESERVE_BYTES",
        "rollbackCreated", "STAGING_PREFIX", "copyFileVerified",
        'MessageDigest.getInstance("SHA-256")', "commitStagingCopy", "TransferRuntime.begin(",
        "TransferCancelledException", "COPY_BUFFER_BYTES = 64 * 1024",
    ),
    "safe transfer primitive",
)
file_operations = ROOT / "app/src/main/java/dev/laxerus/omnifiles/fs/FileOperations.kt"
if file_operations.is_file() and "overwrite = true" in file_operations.read_text(encoding="utf-8"):
    errors.append("file transfer must not silently overwrite existing user files")

require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/fs/TransferRuntime.kt",
    ("data class Snapshot", "fun requestCancel(", "fun isCancelled(", "fun finish(", "fun setListener(", "fun currentSnapshot("),
    "single transfer runtime",
)

require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/fs/TransferBatchRuntime.kt",
    (
        "enum class Phase { PREPARING, ACTIVE, FINISHED }", "data class Snapshot", "preparedItems",
        "processedItems", "settledBytes", "currentBytes", "totalBytes", "fun requestCancel(",
        "fun isCancelled(", "fun finishItem(", "fun finish(", "fun setListener(", "fun currentSnapshot(",
    ),
    "batch transfer runtime",
)

require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/fs/TransferBatchRunner.kt",
    (
        "data class Result<T>", "val remaining: List<T>", "estimateBytes:", "execute:",
        "TransferBatchRuntime.begin", "TransferBatchRuntime.reportPreparation",
        "TransferBatchRuntime.startItem", "TransferBatchRuntime.updateCurrent",
        "TransferBatchRuntime.finishItem", "TransferCancelledException", "items.drop(index)",
    ),
    "batch transfer executor",
)

require_tokens(
    "app/src/test/java/dev/laxerus/omnifiles/fs/TransferBatchRunnerTest.kt",
    (
        "cancellationStopsBeforeNextItemAndKeepsUnprocessedItems",
        "aggregateProgressCombinesFinishedAndCurrentItemBytes",
        "failedItemDoesNotStopFollowingItems",
        "staleCancelRequestCannotCancelNewBatch",
    ),
    "batch transfer regression test",
)

require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/ui/OmniActivity.kt",
    (
        "TransferBatchRuntime.setListener(batchTransferListener)",
        "TransferBatchRuntime.requestCancel(activeId)",
        "renderBatchTransferSnapshot", "transferDialogIsBatch", "R.string.transfer_cancel_batch",
        "R.string.transfer_batch_detail", "R.string.transfer_batch_cancelled_detail",
    ),
    "batch transfer Material UI",
)

require_tokens(
    "app/src/main/res/values/transfer_strings.xml",
    ("transfer_cancel_batch", "transfer_batch_preparing", "transfer_batch_detail", "transfer_batch_cancelled_toast"),
    "batch transfer UI string",
)

require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/fs/BrowserStartPathPolicy.kt",
    ("FilePathPolicy.requireDirectEntry", "fun resolve("),
    "browser start path policy",
)

require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/ui/FileBrowserActivity.kt",
    (
        "TransferMode.COPY", "TransferMode.MOVE", "sourcePaths: List<String>", "pastePendingTransfer",
        "selectedPaths", "selectAllVisible", "shareSelectedFiles", "moveSelectedToTrash", "restoreTrashTickets",
        "FileInspector.inspect", "FavoriteStore", "showFavoritePicker", "toggleCurrentFavorite", "STATE_CURRENT_PATH",
        "restoreTrash", "BrowserStartPathPolicy.resolve", "EXTRA_START_PATH", "TransferBatchRunner.run(",
        "TransferBatchRuntime.Operation.COPY", "TransferBatchRuntime.Operation.MOVE",
        "FileOperations.estimateTransferBytes(", "R.string.transfer_batch_cancelled_toast",
    ),
    "file browser transfer/selection/detail/favorite/batch wiring",
)
file_browser = ROOT / "app/src/main/java/dev/laxerus/omnifiles/ui/FileBrowserActivity.kt"
if file_browser.is_file():
    text = file_browser.read_text(encoding="utf-8")
    if "FileListAdapter(::handleEntryClick, ::handleEntryLongClick, ::showEntryActions)" not in text:
        errors.append("file browser must keep long-press selection separate from the more-actions menu")

require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/fs/FilePathPolicy.kt",
    ("Character.isISOControl", "requireDirectEntry"),
    "local path policy",
)
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/fs/DigestUtils.kt",
    ("SHA-256", "sha256Hex", "BUFFER_BYTES", "toHex"),
    "checksum primitive",
)
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/fs/FileInspector.kt",
    ("DEFAULT_MAX_ENTRIES", "FilePathPolicy.requireInside", "visitedDirectories", "saturatingAdd"),
    "bounded file inspection guard",
)
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/fs/StorageAnalyzer.kt",
    (
        "DEFAULT_MAX_ENTRIES = 40_000", "ArrayDeque<Frame>", "FilePathPolicy.requireDirectEntry",
        "isCancelled", "truncated", "largestFiles", "largestDirectories", "enum class FileCategory",
        "data class CategoryUsage", "IntArray(FileCategory.entries.size)", "LongArray(FileCategory.entries.size)",
        "DEFAULT_CATEGORY_TOP_LIMIT = 8", "MAX_CATEGORY_TOP_LIMIT = 20", "categoryTopLimit",
        "Array(FileCategory.entries.size)", "categoryLargestFiles[categoryIndex]",
        "largestFiles = categoryLargestFiles[index].sortedWith(ranking)",
        "classifyFileName", "categories = categories",
    ),
    "bounded storage analyzer guard",
)
require_tokens(
    "app/src/test/java/dev/laxerus/omnifiles/fs/StorageAnalyzerTest.kt",
    (
        "keepsLargestFilesPerCategoryBoundedAndSorted", "categoryTopLimit = 4",
        "DEFAULT_CATEGORY_TOP_LIMIT", "listOf(30L, 29L, 28L, 27L)",
    ),
    "bounded analyzer category drill-down regression test",
)
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/fs/FavoriteStore.kt",
    ("FilePathPolicy.requireInside", "SharedPreferences", "fun list(", "fun toggle("),
    "favorite path safety/persistence",
)
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/ui/StorageAnalyzerActivity.kt",
    (
        "StorageAnalyzer.scan", "Dispatchers.IO", "AtomicBoolean", "cancelRequested", "largestDirectories",
        "renderCategories", "result.categories", "categoryProgress", "categoryHint", "showCategoryFiles",
        "usage.largestFiles", "storage_analyzer_category_top_title", "FileBrowserActivity.EXTRA_START_PATH",
    ),
    "storage analyzer UI/cancellation/category/navigation wiring",
)
require_tokens(
    "app/src/main/res/values/strings_storage_analyzer.xml",
    (
        "storage_analyzer_category_hint", "storage_analyzer_category_action_hint",
        "storage_analyzer_category_top_title", "storage_analyzer_category_file_item",
    ),
    "storage analyzer category drill-down strings",
)
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbBrowserActivity.kt",
    (
        "AdbFileListAdapter(::openEntry, ::handleLongPress, ::showEntryActions)", "showEntryDetails",
        "copyRemotePath", "RemotePathPolicy.normalizeAbsolute(entry.path)", "ClipData.newUri", "R.string.adb_mode",
    ),
    "ADB browser action/detail safety wiring",
)
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/adb/AdbSessionManager.kt",
    ("suspend fun healthCheck", "snapshotRemote", "before == after", "destination.length() == after.size"),
    "ADB health/pull verification",
)
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbFileListAdapter.kt",
    ("private val onMore", "binding.moreButton.setOnClickListener { onMore(entry) }"),
    "ADB row more-actions callback",
)
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/ui/ChecksumActivity.kt",
    ("ActivityResultContracts.OpenDocument", "DigestUtils::sha256Hex", "ClipboardManager", "STATE_HASH"),
    "checksum tool wiring",
)
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/fs/TrashManager.kt",
    ("METADATA_DIR", "fun listEntries()", "fun restore(entry:", "fun deletePermanently(", "fun emptyTrash()"),
    "persistent trash primitive",
)
trash_manager = ROOT / "app/src/main/java/dev/laxerus/omnifiles/fs/TrashManager.kt"
if trash_manager.is_file() and "overwrite = true" in trash_manager.read_text(encoding="utf-8"):
    errors.append("trash restore must not silently overwrite existing user files")
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/ui/TrashActivity.kt",
    ("TrashManager", "restore(entry)", "deletePermanently(entry)", "emptyTrash()"),
    "trash center action",
)
require_tokens(
    "app/src/main/java/dev/laxerus/omnifiles/ui/MainActivity.kt",
    (
        "StatFs", "renderStorageUsage", "storageUsageProgress", "storage_access_ready",
        "ChecksumActivity::class.java", "StorageAnalyzerActivity::class.java", "storageAnalyzerButton",
        "verifyAdbHealth", ".healthCheck()",
    ),
    "home storage/ADB/checksum/analyzer wiring",
)

layout_tokens = {
    "app/src/main/res/layout/activity_file_browser.xml": (
        "@+id/favoriteToggleButton", "@+id/favoritesButton", "@+id/selectionBar", "@+id/selectAllButton",
        "@+id/selectionShareButton", "@+id/selectionCopyButton", "@+id/selectionMoveButton",
        "@+id/selectionTrashButton", "@+id/pasteButton", "@+id/cancelTransferButton", "@+id/operationProgress",
    ),
    "app/src/main/res/layout/activity_storage_analyzer.xml": (
        "@+id/categoriesContainer", "@+id/filesContainer", "@+id/foldersContainer",
    ),
    "app/src/main/res/layout/item_storage_category.xml": (
        "@+id/categoryName", "@+id/categoryMeta", "@+id/categoryHint", "@+id/categoryProgress",
    ),
    "app/src/main/res/layout/activity_main.xml": (
        "@+id/trashButton", "@+id/checksumButton", "@+id/storageAnalyzerButton",
        "@+id/storageUsageText", "@+id/storageUsageProgress",
    ),
    "app/src/main/res/layout/dialog_transfer_progress.xml": (
        "@+id/transferStatusText", "@+id/transferDetailText", "@+id/transferProgressIndicator",
    ),
}
for path, tokens in layout_tokens.items():
    require_tokens(path, tokens, f"layout contract {path}")

workflow_render = ROOT / "scripts/render_build_apk.sh"
if workflow_render.is_file():
    text = workflow_render.read_text(encoding="utf-8")
    for task in (":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebug"):
        if task not in text:
            errors.append(f"Render APK pipeline missing gate: {task}")

for path in ROOT.glob("app/src/main/java/**/*.kt"):
    text = path.read_text(encoding="utf-8")
    if "Runtime.getRuntime().exec" in text or 'ProcessBuilder("su"' in text:
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
