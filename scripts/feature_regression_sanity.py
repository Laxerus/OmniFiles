#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
errors: list[str] = []


def require(path: str, *tokens: str) -> None:
    file = root / path
    if not file.is_file():
        errors.append(f"missing required file: {path}")
        return
    text = file.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            errors.append(f"{path} missing: {token}")


require(
    "app/src/main/res/layout/activity_file_browser.xml",
    "dev.laxerus.omnifiles.ui.RecentFoldersButton",
    '@+id/recentFoldersButton',
    '@+id/sortDirectionButton',
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/QuickFolderPolicy.kt",
    "SCREENSHOTS",
    "RECORDINGS",
    "DCIM/Screenshots",
    "Pictures/Screenshots",
    "Movies/Screen recordings",
    "FilePathPolicy.requireDirectEntry",
    "firstOrNull()",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/RecentFoldersButton.kt",
    "QuickFolderPolicy.available(sharedRoot)",
    "RecentFolderStore",
    "RecentFileStore",
    "recentFolderStore.clear()",
    "recentFileStore.clear()",
    "LocalFileIntents.viewIntent",
    "QuickFolderPolicy.Kind.SCREENSHOTS",
    "QuickFolderPolicy.Kind.RECORDINGS",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/RecentFolderStore.kt",
    "RecentFolderHistoryPolicy.normalize",
    "RecentFolderHistoryPolicy.push",
    "if (validPaths != stored) persist(validPaths)",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/RecentFileStore.kt",
    "RecentFileHistoryPolicy.normalize",
    "RecentFileHistoryPolicy.push",
    "if (validPaths != stored) persist(validPaths)",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/FileListAdapter.kt",
    "recordRecentDirectory",
    "recordRecentFile",
    "if (!selectionMode)",
)
require(
    "app/src/test/java/dev/laxerus/omnifiles/fs/QuickFolderPolicyTest.kt",
    "prefersFirstAvailableAliasAndFallsBackWhenMissing",
    "unsafePrimaryAliasDoesNotBlockSafeFallbackAlias",
    "rejectsSymlinkedQuickFolderThatEscapesSharedRoot",
)
require(
    "app/src/test/java/dev/laxerus/omnifiles/fs/RecentFolderHistoryPolicyTest.kt",
    "rootIsNeverAddedAsRecentFolder",
)
require(
    "app/src/test/java/dev/laxerus/omnifiles/fs/RecentFileHistoryPolicyTest.kt",
    "newestFileMovesToFrontAndDuplicatesCollapse",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/DuplicateFinder.kt",
    "fun verifyDuplicate(",
    "expectedSha256",
    "FilePathPolicy.requireDirectEntry",
    "sha256(safe, isCancelled)",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/DuplicateFinderActivity.kt",
    "DuplicateFinder.verifyDuplicate",
    "duplicate_finder_keeper_stale",
    "duplicate_finder_keeper_changed_during_cleanup",
    "duplicate_finder_pipeline_summary",
    "!verifyDuplicate(duplicate, group.sha256)",
)
require(
    "app/src/test/java/dev/laxerus/omnifiles/fs/DuplicateFinderTest.kt",
    "verifyDuplicateAcceptsUnchangedVerifiedFile",
    "verifyDuplicateRejectsContentChangedWithSameSizeAndTimestamp",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/StorageAnalyzer.kt",
    "fun verifyUnchangedFile(",
    "FilePathPolicy.requireDirectEntry(File(entry.path), safeRoot)",
    "safe.length().coerceAtLeast(0L) != entry.sizeBytes",
    "safe.lastModified().coerceAtLeast(0L) != entry.modifiedAt",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/StorageAnalyzerActivity.kt",
    "TrashManager",
    "StorageAnalyzer.verifyUnchangedFile",
    "storage_analyzer_move_to_trash",
    "storage_analyzer_trash_moving",
    "trashManager.moveToTrash(safe)",
)
require(
    "app/src/test/java/dev/laxerus/omnifiles/fs/StorageAnalyzerTest.kt",
    "verifyUnchangedFileAcceptsScannedFileAndRejectsDirectories",
    "verifyUnchangedFileRejectsChangedSizeAndTimestamp",
    "verifyUnchangedFileRejectsSymlinkThatEscapesRoot",
)
require(
    "app/src/main/res/values/strings_storage_analyzer.xml",
    "storage_analyzer_move_to_trash",
    "storage_analyzer_trash_confirm",
    "storage_analyzer_trash_moving",
    "storage_analyzer_trashed",
    "storage_analyzer_trash_failed",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/FileBrowserPreferences.kt",
    "saveSortDescending",
    "descendingFor",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/FileBrowserSorter.kt",
    "descending: Boolean",
    "if (descending) -primary else primary",
    "NaturalNameComparator",
)

if errors:
    print("OmniFiles feature regression sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles feature regression sanity: OK")
