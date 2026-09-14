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
else:
    errors.append("missing AndroidManifest.xml")

require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/FileManagerToolbar.kt",
    "inflateMenu(R.menu.menu_file_browser)",
    "R.id.actionSettingsTools -> MainActivity::class.java",
    "R.id.actionStorageAnalyzer -> StorageAnalyzerActivity::class.java",
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
    '@+id/actionStorageAnalyzer',
    '@+id/actionTrash',
    '@+id/actionChecksum',
    '@+id/actionWirelessAdb',
    '@drawable/ic_settings_24',
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
    'app:cardBackgroundColor="?attr/colorSecondaryContainer"',
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/MainActivity.kt",
    "binding.toolbar.setNavigationOnClickListener { finish() }",
    "bindActionCards()",
    "binding.storageAccessCard.setOnClickListener",
    "binding.developerSettingsCard.setOnClickListener",
    "binding.wifiSettingsCard.setOnClickListener",
    "binding.appDetailsCard.setOnClickListener",
    "binding.storageAnalyzerButton.setOnClickListener",
    "binding.trashButton.setOnClickListener",
    "binding.checksumButton.setOnClickListener",
    "binding.adbCard.setOnClickListener",
    "binding.adbFilesCard.setOnClickListener",
    "binding.saveScoutCard.setOnClickListener",
    "binding.sqliteStudioCard.setOnClickListener",
    "binding.storageStatusChip",
    "binding.adbStatusChip",
    "binding.rootStatusChip",
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
    '@+id/storageAnalyzerButton',
    '@+id/trashButton',
    '@+id/checksumButton',
    '@+id/adbCard',
    '@+id/adbFilesCard',
    '@+id/saveScoutCard',
    '@+id/sqliteStudioCard',
    "dev.laxerus.omnifiles.ui.SettingsActionCard",
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

for drawable in (
    "ic_chevron_right_24.xml",
    "ic_security_24.xml",
    "ic_search_24.xml",
    "ic_database_24.xml",
):
    if not (root / "app/src/main/res/drawable" / drawable).is_file():
        errors.append(f"missing modern settings drawable: {drawable}")

if errors:
    print("OmniFiles file-manager shell sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles file-manager shell sanity: OK")
