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


manifest = root / "app/src/main/AndroidManifest.xml"
if manifest.is_file():
    text = manifest.read_text(encoding="utf-8")
    file_browser = text.find('android:name=".ui.FileBrowserActivity"')
    launcher = text.find('<category android:name="android.intent.category.LAUNCHER"')
    main = text.find('android:name=".ui.MainActivity" android:exported="false"')
    if min(file_browser, launcher, main) < 0 or not (file_browser < launcher):
        errors.append("FileBrowserActivity must remain the exported MAIN/LAUNCHER and MainActivity must remain internal")
    if text.count('android.intent.category.LAUNCHER') != 1:
        errors.append("manifest must expose exactly one launcher entry")
    if 'android:name=".ui.JunkCleanerActivity" android:exported="false"' not in text:
        errors.append("JunkCleanerActivity must remain internal")
    if 'android:name=".ui.DuplicateFinderActivity" android:exported="false"' not in text:
        errors.append("DuplicateFinderActivity must remain internal")
else:
    errors.append("missing AndroidManifest.xml")

require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/FileManagerToolbar.kt",
    "inflateMenu(R.menu.menu_file_browser)",
    "R.id.actionSettingsTools -> MainActivity::class.java",
    "R.id.actionJunkCleaner -> JunkCleanerActivity::class.java",
    "R.id.actionStorageAnalyzer -> StorageAnalyzerActivity::class.java",
    "R.id.actionDuplicateFinder -> DuplicateFinderActivity::class.java",
    "R.id.actionTrash -> TrashActivity::class.java",
    "R.id.actionChecksum -> ChecksumActivity::class.java",
    "R.id.actionWirelessAdb -> AdbPairingActivity::class.java",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/StorageAccessButton.kt",
    "StorageAccessController::requestSharedStorageAccess",
    "hasSharedStorageAccess(context)",
    "onWindowFocusChanged",
)
require(
    "app/src/main/res/layout/activity_file_browser.xml",
    "dev.laxerus.omnifiles.ui.FileManagerToolbar",
    "dev.laxerus.omnifiles.ui.StorageAccessButton",
    '@+id/storageAccessButton',
)
require(
    "app/src/main/res/menu/menu_file_browser.xml",
    '@+id/actionSettingsTools',
    '@+id/actionJunkCleaner',
    '@+id/actionStorageAnalyzer',
    '@+id/actionDuplicateFinder',
    '@+id/actionTrash',
    '@+id/actionChecksum',
    '@+id/actionWirelessAdb',
    '@drawable/ic_settings_24',
    '@drawable/ic_content_copy_24',
)

require(
    "app/src/main/java/dev/laxerus/omnifiles/access/SystemSettingsNavigator.kt",
    "Destination.ALL_FILES_ACCESS",
    "Destination.DEVELOPER_OPTIONS",
    "Destination.WIFI",
    "Destination.APP_DETAILS",
    "Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
    "Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS",
    "Settings.ACTION_WIFI_SETTINGS",
    "Settings.ACTION_APPLICATION_DETAILS_SETTINGS",
    "Intent(Settings.ACTION_SETTINGS)",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/access/StorageAccessController.kt",
    "SystemSettingsNavigator.Destination.ALL_FILES_ACCESS",
    "Manifest.permission.READ_EXTERNAL_STORAGE",
    "Manifest.permission.WRITE_EXTERNAL_STORAGE",
    "readGranted && writeGranted",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/AdbPairingActivity.kt",
    "SystemSettingsNavigator.Destination.DEVELOPER_OPTIONS",
    "SystemSettingsNavigator.Destination.WIFI",
)
require(
    "app/src/main/res/layout/activity_adb_pairing.xml",
    '@+id/settingsButton',
    '@+id/wifiSettingsButton',
    'app:cardCornerRadius="24dp"',
)

require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/SettingsActionCard.kt",
    "class SettingsActionCard",
    "R.layout.view_settings_action_card",
    "fun bind(",
    "fun setSummary(",
    "contentDescription",
    "android.R.attr.selectableItemBackground",
)
require(
    "app/src/main/res/layout/view_settings_action_card.xml",
    '@+id/actionIcon',
    '@+id/actionTitle',
    '@+id/actionSummary',
    '@drawable/ic_chevron_right_24',
    'app:cardBackgroundColor="?attr/colorPrimaryContainer"',
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/MainActivity.kt",
    "binding.toolbar.setNavigationOnClickListener { finish() }",
    "bindActionCards()",
    "binding.storageAccessCard.setOnClickListener",
    "binding.developerSettingsCard.setOnClickListener",
    "binding.wifiSettingsCard.setOnClickListener",
    "binding.appDetailsCard.setOnClickListener",
    "binding.junkCleanerButton.setOnClickListener",
    "binding.storageAnalyzerButton.setOnClickListener",
    "binding.duplicateFinderButton.setOnClickListener",
    "DuplicateFinderActivity::class.java",
    "binding.trashButton.setOnClickListener",
    "binding.checksumButton.setOnClickListener",
    "binding.adbCard.setOnClickListener",
    "binding.adbFilesCard.setOnClickListener",
    "binding.saveScoutCard.setOnClickListener",
    "binding.sqliteStudioCard.setOnClickListener",
    "binding.storageStatusChip",
    "binding.adbStatusChip",
    "binding.rootStatusChip",
    "openStorageTool(",
    "openAdbTool(",
    "openStorageAccessSettings()",
    "SystemSettingsNavigator.Destination.DEVELOPER_OPTIONS",
    "SystemSettingsNavigator.Destination.WIFI",
    "SystemSettingsNavigator.Destination.APP_DETAILS",
)
require(
    "app/src/main/res/layout/activity_main.xml",
    '@+id/toolbar',
    '@+id/storageStatusChip',
    '@+id/adbStatusChip',
    '@+id/rootStatusChip',
    '@+id/storageAccessCard',
    '@+id/developerSettingsCard',
    '@+id/wifiSettingsCard',
    '@+id/appDetailsCard',
    '@+id/junkCleanerButton',
    '@+id/storageAnalyzerButton',
    '@+id/duplicateFinderButton',
    '@+id/trashButton',
    '@+id/checksumButton',
    '@+id/adbCard',
    '@+id/adbFilesCard',
    '@+id/saveScoutCard',
    '@+id/sqliteStudioCard',
    "dev.laxerus.omnifiles.ui.SettingsActionCard",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/JunkCleaner.kt",
    "object JunkCleaner",
    "DEFAULT_MAX_ENTRIES",
    "skippedTopLevelDirectories",
    "FilePathPolicy.requireDirectEntry",
    "refreshed.modifiedAt == scannedCandidate.modifiedAt",
    "val removed = safe.delete()",
    "classifyFile",
    "classifyDirectory",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/JunkCleanerActivity.kt",
    "JunkCleaner.scan",
    "JunkCleaner.clean",
    "AutoCleanupManager",
    "runAutomaticCleanupNow",
    "renderAutoCleanupStatus",
    "binding.autoCleanupSwitch",
    "binding.autoCleanupStatus",
    "MaterialAlertDialogBuilder",
    "TrashActivity::class.java",
)
require(
    "app/src/main/res/layout/activity_junk_cleaner.xml",
    '@+id/scanButton',
    '@+id/cleanButton',
    '@+id/openTrashButton',
    '@+id/summaryText',
    '@+id/detailsText',
    '@+id/autoCleanupSwitch',
    '@+id/autoCleanupStatus',
)
require(
    "app/src/main/res/values/strings_settings_hub.xml",
    "settings_overview_title",
    "settings_section_phone",
    "settings_section_files",
    "settings_section_adb",
    "settings_section_developer",
    "settings_storage_access_ready_summary",
    "settings_analyzer_needs_access",
    "settings_adb_browser_needs_setup",
    "settings_save_scout_needs_setup",
    "settings_status_storage_ready",
    "settings_status_adb_connected",
    "settings_status_root_detected",
    "settings_requires_adb",
)
require(
    "app/src/main/res/values/strings_junk_cleaner.xml",
    "junk_cleaner_title",
    "junk_cleaner_clean_now",
    "junk_cleaner_safety_note",
    "junk_cleaner_auto_title",
    "junk_cleaner_auto_help",
    "junk_cleaner_auto_last_run",
    "settings_junk_cleaner_summary",
    "toolbar_junk_cleaner",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/maintenance/AutoCleanupSchedule.kt",
    "object AutoCleanupSchedule",
    "NORMAL_INTERVAL_MS",
    "RETRY_INTERVAL_MS",
    "fun shouldRun(",
    "if (!enabled || !hasStorageAccess) return false",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/maintenance/AutoCleanupManager.kt",
    "object AutoCleanupManager",
    "fun state(context: Context)",
    "fun setEnabled(context: Context, enabled: Boolean)",
    "fun runIfDue(",
    "AutoCleanupSchedule.shouldRun",
    "JunkCleaner.scan",
    "JunkCleaner.clean",
)
require(
    "app/src/test/java/dev/laxerus/omnifiles/maintenance/AutoCleanupScheduleTest.kt",
    "firstRunIsImmediatelyDue",
    "successfulRunIsLimitedToOncePerDay",
    "failedAttemptWaitsAtLeastOneHourBeforeRetry",
    "forceBypassesTimeWindowsButNotSafetyGates",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/fs/DuplicateFinder.kt",
    "object DuplicateFinder",
    "DEFAULT_MAX_ENTRIES",
    "DEFAULT_MAX_HASHED_FILES",
    "FilePathPolicy.requireDirectEntry",
    "MessageDigest.getInstance(\"SHA-256\")",
    "reclaimableBytes",
    "isCancelled",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/DuplicateFinderActivity.kt",
    "DuplicateFinder.scan",
    "BrowserLaunchExtras.EXTRA_HIGHLIGHT_PATH",
    "duplicate_finder_access_required",
    "copyGroupPaths",
    "TrashManager",
    "moveToTrash",
)
require(
    "app/src/main/res/layout/activity_duplicate_finder.xml",
    '@+id/startButton',
    '@+id/cancelButton',
    '@+id/progress',
    '@+id/groupsContainer',
)
require(
    "app/src/main/res/values/strings_duplicate_finder.xml",
    "duplicate_finder_title",
    "settings_duplicate_finder_summary",
    "settings_duplicate_finder_needs_access",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/maintenance/StartupMaintenance.kt",
    "object StartupMaintenance",
    'Rule("adb-preview"',
    'Rule("adb-checksum"',
    'Rule("sqlite-studio"',
    "DEFAULT_MAX_ENTRIES",
    "safeEntry",
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/OmniFilesApp.kt",
    "StartupMaintenance.prune(cacheDir)",
    "AutoCleanupManager.runIfDue(this@OmniFilesApp)",
    '"omnifiles-startup-maintenance"',
)

for drawable in (
    "ic_chevron_right_24.xml",
    "ic_security_24.xml",
    "ic_search_24.xml",
    "ic_database_24.xml",
    "ic_content_copy_24.xml",
):
    if not (root / "app/src/main/res/drawable" / drawable).is_file():
        errors.append(f"missing modern settings drawable: {drawable}")

if errors:
    print("OmniFiles file-manager shell sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles file-manager shell sanity: OK")
