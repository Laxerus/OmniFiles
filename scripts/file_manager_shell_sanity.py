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
    "R.id.actionSettingsTools",
    "Intent(context, MainActivity::class.java)",
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
    '@drawable/ic_settings_24',
    '@string/settings_tools',
)
require(
    "app/src/main/java/dev/laxerus/omnifiles/ui/MainActivity.kt",
    "binding.openFilesButton.setOnClickListener { finish() }",
)
require(
    "app/src/main/res/values/strings_navigation.xml",
    "settings_hub_title",
    "settings_hub_subtitle",
    "grant_access_inline",
)

if errors:
    print("OmniFiles file-manager shell sanity: FAILED")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("OmniFiles file-manager shell sanity: OK")
